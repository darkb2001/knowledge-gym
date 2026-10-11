# Observability runbook

## First response

1. Check application health and existing Prometheus dashboards first.
2. Identify failing telemetry component; do **not** restart the application
   merely because logs/traces are missing.
3. Inspect Pipeline Health: up, accepted records/spans, queue utilization,
   export failure/refusal/drop counters and Collector RSS.
4. Use existing Alertmanager route; do not add a separate notification channel
   or print its receiver configuration/credentials.

## Logs and traces

Application Overview & Logs: choose environment/service/level; request/trace
filters are optional ID prefixes. Paste a full ID for effective exact matching.
Expand a line and select Open trace. Missing traces can be normal at 5% root
sampling, during retention mismatch, or after telemetry drops.
Nginx generates an ID even for edge 429/404; a request rejected before reaching
Spring has no upstream trace ID. `request_id` is promoted for nginx too, so the
same 32-hex ID joins an edge rejection to the application line it produced (the
app logs the incoming ID from `RequestTelemetryFilter`). Only a value matching
`^[0-9a-f]{32}$` is promoted; anything else is deleted from the body.

Explore Loki (no `| json`: every filter below reads structured metadata that the
Collector promoted, so a query never re-parses the log body):
```logql
{service_name="knowledge-gym", deployment_environment_name="production"}
  | severity_text="ERROR"
{service_name="knowledge-gym"} | request_id="..." | trace_id="..."
{service_name="knowledge-gym"} | http_route="/notes/{id}" | user_id="<account-uuid>"
```
Promoted fields: `severity_text`, `trace_id`, `span_id`, `request_id`,
`http_route`, `user_id`. Any other body key must still be reached with `| json`.
`user_id` is the account UUID bound by `UserMdcFilter`; anonymous, actuator and
pre-authentication lines have no `user_id` at all, so a non-empty filter also
answers "which accounts hit this route". Only the reviewed shapes are promoted:
a raw path/query or a non-UUID actor is deleted at the Collector, so those lines
never match and cannot be retrieved. Stream labels stay at two (`service_name`,
`deployment_environment_name`) — do not use IDs/emails/IPs as Loki labels.

Application Overview & Logs carries the same filters: `Route` and `User ID` are
paste/clear textboxes and the two tables below the log panel (top routes, top
actors) are clickable — a click re-opens the dashboard with that route or actor
in the box. A box left empty is a no-op, not a filter: Loki matches an absent
field against `.*`, so the default view still shows lines that carry no route or
no actor at all (verified: filtered and unfiltered counts are identical). The
route filter is a prefix match, so selecting `/blog/posts/{slug}` also shows
nested paths under it.
Grafana 12 Logs Drilldown on the Loki datasource is the fast path for browsing
volume by service/pattern before writing LogQL; it is read-only and does not
change what is stored. With the SSH tunnel up it lives at
http://localhost:3001/a/grafana-lokiexplore-app/explore (Traces Drilldown:
/a/grafana-exploretraces-app/explore), and it needs a logged-in session —
anonymous access stays off.
Explore Tempo searches by exact trace ID or the provisioned TraceQL view.
Trace → Logs for this span searches ±2m; metrics links show the corresponding
time window. Exemplar storage must be enabled in Prometheus and the span must
have been sampled. Histogram p95 is approximate with finite SLO buckets.

## Log dashboards: editing and trusting a panel

The log panels filter on structured metadata (`| severity_text=… | http_route=…`),
so no predicate should need `| json`; a body parse means a field is missing from
the promotion allowlist in `ops/observability/collector.yml` rather than a reason
to add one back. After any panel or provisioning change, run
`scripts/qa-dashboard-logs.py` on CT102: it reprovisions and then replays every
panel query through Grafana exactly as the browser would, so "it renders for me"
is not the only evidence. Loki rejects a stream selector whose matchers are *all*
empty-compatible (`{a=~".*", b=~".*"}` → 400), log panels must be queried as
range queries, and `$__rate_interval` is not expanded for Loki (use `$__range`).
Details and the recorded post-deploy numbers are in
`docs/observability-deployment.md`.

## Missing logs / parsing errors

Check Docker log driver, logging labels, configured DockerRootDir/containers
bind path and file readability. Collector has no socket, so it cannot discover
Compose metadata from the Docker API. Labels are embedded by Docker json-file
only after the container is recreated with the new logging options.
Raw/malformed records are intentionally dropped quietly; accepted-log metrics
do not prove every application event was collected.

New files start at end. Persisted offsets resume existing files after a graceful
restart; Docker rotation/deletion can lose data not read in time. Native canary
verifies a graceful restart, not abrupt host death or all rotation races.

## Backend down, full queue or memory refusal

A health-check HTTP response means Collector process health, not successful
backend delivery. Check export counters and actual queried data.
Each exporter queue is 64 requests; retry stops after 60s. Data can be dropped
on queue overflow, permanent rejection, crash or prolonged outage.
Keep application traffic flowing; fix backend connectivity/limits/disk first.
If CT resource pressure threatens business services, disable tracing or stop
only observability services in an approved maintenance action.

After recovery: send synthetic canary, query logs and trace, check queue drains
and alerts resolve. Do not claim lossless recovery: exporter queues are not
persisted. file_storage persists read offsets, not durable delivery receipts.

## Capacity and disk

Before rollout and at least daily during initial soak:
```bash
free -m
df -h / /var/lib/docker
docker stats --no-stream
```

Check the actual Docker root if not /var/lib/docker. Inspect volume growth without
printing file contents; measure Loki/Tempo WAL and blocks. Soft initial budget
6 GiB including WAL/compaction headroom, at least 10 GiB host free.
If free space drops below 10 GiB or grows unexpectedly, stop ingestion under
operator approval and investigate. Retention deletion is asynchronous and needs
space to compact; lowering retention is not an immediate disk cleanup.

Never delete Docker data directories or run compose down -v during an incident.
Do not move logs to Elasticsearch or Garage as a quick workaround.
There is no node-exporter filesystem gauge added in this change; early host
capacity monitoring remains an explicit operator responsibility. Export-failure
alerts catch backend write failures, not advance disk exhaustion.

## Trace volume, and the Patterns tab

The app exports **every** trace (`OTEL_TRACES_SAMPLER=parentbased_traceidratio`,
`OTEL_TRACE_SAMPLE_RATIO=1.0` in `docker-compose.observability.yml`); the only
traffic dropped is scrape traffic, and it is dropped per **whole trace** by the
`tail_sampling` processor in `ops/observability/collector.yml`. Do not replace it
with a span-level filter: removing a trace's root span leaves its children behind
as orphans that still show up as traces in Tempo. A trace therefore becomes visible
in Tempo only after `decision_wait` (15s) — a fresh trace appearing 15-20s late is
normal, not a pipeline failure; `scripts/test-observability-pipeline.py` waits for
that window explicitly.

```bash
# is the drop policy still firing?
IP=$(docker inspect -f '{{range .NetworkSettings.Networks}}{{.IPAddress}} {{end}}' kgops-otel-collector-1 | awk '{print $1}')
curl -s "http://$IP:8888/metrics" | grep 'tail_sampling_count_traces_sampled'
# and end to end, on the live stack:
python3 scripts/qa-dashboard-logs.py   # asserts 0 actuator traces in the last 5m
```
To change what is dropped, edit the `IsMatch(...)` condition in the `drop-scrapes`
policy and keep it **unanchored**: `http.route` carries the servlet context path
(`/api/v1/actuator/prometheus`, not `/actuator/prometheus`). Validate the file with
the Collector image before recreating it (`otelcol-contrib validate --config`).

Turning the Patterns tab into a working tab (do not expect content today):
uncomment the `pattern_ingester:` block in `ops/observability/loki.yml` and recreate
Loki. For the app stream it stays empty until the JSON `message` field carries
variable text — the tokenizer rejected our lines as `too_few_tokens` (302 of them in
the 2026-10-11 trial) because `message` is the constant `http_request_completed`, and
`/loki/api/v1/patterns` answered `data: []` even for a catch-all matcher. Judge the
result with that endpoint, not with the `loki_pattern_*` tee metrics, which look
healthy even when nothing is queryable.

## Alerts

Six rules: backend down (3m), log/trace export failures (5m),
queue >80% (5m), drops/refusals (3m), traffic but no accepted logs (15m).
The last rule detects total pipeline silence, not app-only loss when another
service still emits logs. Verify exact metric names/series on staging.
Intentional linked-span/event filtering is privacy policy, not an exporter failure.

## Safe rollback

See [deployment](observability-deployment.md). Turning tracing off first protects
app availability; leaving volumes/backends available preserves retained evidence.
Removing the host opt-in marker alone does not alter running containers.
Restoring old app image needs matching old Compose/env/config decisions.
