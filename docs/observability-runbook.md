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
Spring has no upstream trace ID.

Explore Loki:
```logql
{service_name="knowledge-gym", deployment_environment_name="production"}
  | json | level="ERROR"
```

For a known ID, add | request_id="..." or | trace_id="..." — no `| json` needed.
Route and actor are structured metadata, so they filter the same way:
```logql
{service_name="knowledge-gym"} | http_route="/notes/{id}" | user_id="<account-uuid>"
```
`user_id` is the account UUID bound by `UserMdcFilter`; anonymous, actuator and
pre-authentication lines have no `user_id` at all, so a non-empty filter also
answers "which accounts hit this route". Only the reviewed shapes are promoted:
a raw path/query or a non-UUID actor is deleted at the Collector, so those lines
never match and cannot be retrieved.
Do not use IDs/emails/IPs as Loki stream labels.
Grafana 12 Logs Drilldown on the Loki datasource is the fast path for browsing
volume by service/pattern before writing LogQL; it is read-only and does not
change what is stored.
Explore Tempo searches by exact trace ID or the provisioned TraceQL view.
Trace → Logs for this span searches ±2m; metrics links show the corresponding
time window. Exemplar storage must be enabled in Prometheus and the span must
have been sampled. Histogram p95 is approximate with finite SLO buckets.

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
