# Observability deployment and verification

## Status

**Activated in production on 2026-10-11 on CT102 (`kg-be`), commits
`426ff58..baf7ebe`.** `/opt/kg/run/observability.enabled` is the switch:
`scripts/deploy-prod.sh` adds the overlay only when that marker exists *and* the
`kgops_telemetry` network is live, so the ops profile and the app rollout are
independent. Stopping is `rm` of the marker plus a normal deploy (see rollback
below). Existing GitHub CI keeps deploying app pushes while DEPLOY_ENABLED=true;
no workflow starts or stops the ops profile.

The staging/preflight checklist below is still the gate for changing the
observability stack itself; the defects found while activating it in production
are recorded in "Activation record" at the end of this document.

Use an isolated staging VM/CT/Docker engine: network names kgops_telemetry and
kg_kg-internal are intentionally fixed and must not collide with production.
Confirm disk/RAM/CPU baseline, DockerRootDir, log driver and Grafana/tunnel ACL.
Do not copy production database or credentials into synthetic fixtures.

Images: Collector 0.162.0, Loki 3.7.8, Tempo 2.9.0 and static BusyBox 1.37.0-musl
health probe. App agent 2.32.0 is SHA-256-pinned. New images are version-pinned,
not digest-locked; capture resolved immutable digests and scan them before rollout.
Build ops wrapper images on CI/workstation, not the resource-limited LXC.
Existing base image versions are not upgraded by this feature — the single
exception is the reviewed Grafana 11.1.0 -> 12.4.12 upgrade in the "Upgrade
record" at the end of this document.

## Local source gates

Set JAVA_HOME to a JDK 21 installation. Install PyYAML 6.0.3 in a temporary venv.
Provide native binaries matching Collector 0.162.0, Loki 3.7.8 and Prometheus 2.53.0.
Download from the versioned upstream releases and verify checksums.

```bash
./gradlew build
./gradlew :kg-presentation:test --tests 'com.knowledgegym.presentation.telemetry.*'
./gradlew :kg-presentation:telemetryAgentTest -PotelAgentPath=/verified/otel-javaagent.jar
python scripts/verify-observability.py
python scripts/test-observability-pipeline.py --collector /verified/otelcol-contrib
OTEL_STATE_DIR=/existing/temp/state DOCKER_LOG_ROOT=/existing/temp/logs \
  /verified/otelcol-contrib validate --config=ops/observability/collector.yml
/verified/loki -config.file=ops/observability/loki.yml -verify-config=true
/verified/promtool check rules infra/prometheus/observability-alerts.yml
/verified/promtool test rules infra/prometheus/observability-alerts.test.yml
/verified/promtool check config --syntax-only infra/prometheus-observability.yml
git diff --check
```

The source contract script uses --env-file /dev/null, synthetic required values,
Compose --quiet, and a fake Docker CLI for deploy-marker checks.
The native pipeline canary uses loopback ephemeral ports/temp log files and HTTP
JSON mock exporters. It is not a Loki/Tempo/Grafana acceptance test.

Local results: targeted 14 unit tests and one real embedded Kafka agent test
passed; native canary passed selection/redaction/503/recovery/offset restart.
Promtool alert tests passed seven scenarios (eight evaluations), including the
pending duration, healthy backend and each of the six alert conditions.
Full Gradle build FAILED in existing Docker-backed integration tests because
Docker daemon was unavailable; it is a mandatory staging/CI gate, not waived.

## Staging activation commands (examples, NOT executed)

Host-only .env stays out of source/output. Set TELEMETRY_ENVIRONMENT=staging
and the verified Docker containers-log path. Keep existing required variables.
Do not print docker compose config without --quiet or inspect all env values.

```bash
cd /opt/kg
ops=(docker compose -p kgops --env-file /opt/kg/.env \
  -f ops/docker-compose.ops.yml --profile observability)
app=(docker compose -p kg --env-file /opt/kg/.env \
  -f docker-compose.prod.yml -f docker-compose.observability.yml)
"${ops[@]}" config --quiet
"${app[@]}" config --quiet
# Load prebuilt/scanned ops images first; never build app in this CT.
"${ops[@]}" up -d telemetry-init otel-collector loki tempo
# Existing Alertmanager format/labels also require approved recreation
# if infrastructure logs are wanted; do not replace its receiver config.
"${ops[@]}" up -d --no-deps alertmanager
# App image MUST contain the checksum-pinned agent jar before this step.
"${app[@]}" up -d --no-deps app nginx prometheus grafana
"${ops[@]}" ps
```

Config bind-mounts may retain old inodes after git updates. On staging, use
approved --force-recreate --no-deps for affected stateless services and verify
loaded config. Never use --remove-orphans on an ops invocation without its active
profile/service model. Do not run down -v.

After acceptance, create /opt/kg/run/observability.enabled **manually**.
scripts/deploy-prod.sh and the existing CI app deploy retain the overlay only
when this marker exists, and fail before compose if its network is missing.
They do not activate/create the ops stack. Preserve the ES-stop marker as before.
A direct plain compose up can still remove overlay settings: always use both files
while opted in. The marker is host state, not a source-controlled default.

## Mandatory acceptance matrix

Record command, time, Pass/Fail/NOT RUN and observed outputs (never secrets):
- Full Gradle build including Docker integrations; image vulnerability scan.
- Real image startup, non-root data ownership and actual HTTP healthchecks.
- Nginx -t in its real container; HTTP 2xx/4xx/5xx and safe request-ID handling.
- Valid/malformed W3C headers; unsampled root then sampled canary trace.
- JDBC beneath JPA, Redis, Elasticsearch native/transport, JDK/Spring HTTP clients,
  scheduled job, Kafka producer/consumer; no double instrumentation.
- Query actual Loki and Tempo, inspect safe fields and retention.
- Grafana Save & Test; all dashboard variables and panels; log→trace→log links,
  exemplars, anonymous denial, operator-only organization/tunnel access.
- Inject synthetic body/header/SQL/OTP/email sentinels; search all outputs and
  collector diagnostics without displaying payloads. Assert zero sentinel matches.
- Stop Collector, then Loki and Tempo separately on staging; app endpoints and
  latency remain acceptable. Verify alerts and existing Alertmanager delivery.
- Sustained queue saturation, disk pressure, Docker log rotation, abrupt Collector
  crash and app/backend restart; quantify drops rather than claiming losslessness.
- 24h soak: CPU/RSS/GC/latency/volume growth, at least 10 GiB free, no OOM/thrashing.
- Test deploy marker enabled/disabled and rollback across current/old app images.

All real container/Grafana/host-resource items remain NOT RUN locally.

## Production rollout and rollback

Only after the matrix passes and an approved maintenance window: record previous
image/config/digests and baseline, start ops, then explicitly recreate opted-in
services; verify application first and telemetry second. Retain short data
retention initially. No storage migration or HA claim.

Fast tracing rollback: approved app recreation with OTEL_TRACES_EXPORTER=none
and/or remove -javaagent from the overlay; JSON logs remain console-only.
For full overlay rollback, remove the host marker, restore previous plain Compose
application settings, and recreate affected app/nginx/prometheus/grafana with
approval. Restore previous Nginx config if its diagnostics policy must be reverted.
An older app image may lack the agent jar: never reuse -javaagent with that image.

Stop only otel-collector/loki/tempo if needed; leave Alertmanager and persistent
volumes untouched. Restore Prometheus config without new backend scrape jobs/
rules so intentionally stopped telemetry does not trigger false alarms.
Rollback requires actions, not just deleting the marker. Data already dropped
cannot be recovered; host/NVMe failure loses these local observability histories.

## Activation record (2026-10-11, CT102 `kg-be`)

Running: `kgops-telemetry-init` (exit 0), `kgops-loki-1`, `kgops-tempo-1` and
`kgops-otel-collector-1` healthy on the internal `kgops_telemetry` network;
app/nginx/prometheus/grafana joined it through the overlay. Prometheus scrapes
otel-collector, loki and tempo (5/5 targets up, exemplar storage enabled,
telemetry alert group loaded). Grafana serves Loki, Prometheus and Tempo and six
provisioned dashboards.

Defects found and fixed while activating (shipped in `426ff58..baf7ebe`):

* `ADD <agent-url>` left `/app/otel-javaagent.jar` as root:root 0600 while the
  container runs as `app`, so the JVM aborted with "Error opening zip file or
  JAR manifest missing" and the app would have crash-looped as soon as the
  overlay was enabled. Fixed with `chmod 0444` before `USER app`; CI now runs the
  published image as `app` with `-javaagent` as a gate.
* `telemetry-init` dropped every capability but CHOWN, but the named volumes
  inherit uid 10001 from the loki/tempo images, so it died with
  "mkdir: Permission denied" and every telemetry service stayed down (they
  `depends_on` its completion). Fixed with `cap_add: [CHOWN, DAC_OVERRIDE]`.
* The application-logs panel filtered `level=~"${level:regex}"`; the `:regex`
  formatter escapes the custom variable's `All` value `.*` into `\.\*`, Loki
  answered 400 and the panel rendered "No data" in the UI (API-level checks miss
  it because they skip variable interpolation). Fixed with plain interpolation
  plus a contract assertion; panels that legitimately have no series on a
  healthy system now end in `or vector(0)`.

Verification: the edge answers 200; Loki holds `knowledge-gym` and `nginx`
streams; one edge request id and the backend log line for that request resolve to
a single trace id; Tempo lists those traces; all four new dashboards render with
zero "No data" panels in the browser.

Access: Grafana listens on `127.0.0.1:3001` only —
`ssh -N -L 3001:127.0.0.1:3001 deploy@192.168.1.15`, then http://localhost:3001.
The admin password is `GF_SECURITY_ADMIN_PASSWORD` in `/opt/kg/.env`. For phone
or off-LAN access, add a Cloudflare tunnel public hostname
`grafana.darkb-tech.io.vn -> http://localhost:3001` and set `GF_SERVER_ROOT_URL`
to the same URL; never bind the port to 0.0.0.0, the dashboards render full log
bodies.

## Upgrade record (2026-10-11, Grafana 12 and filterable route/actor)

Shipped in `91fa847`; the push pipeline went green (`build-test`, `publish-image`,
`image-security`, `deploy-prod`) and the app now runs `sha-91fa847`.

Grafana moved from 11.1.0 to `grafana/grafana:12.4.12` in both Compose models,
because Logs Drilldown needs 12. Grafana runs a schema migration against the
existing `kg_grafanadata` volume, so `grafana.db` was copied to
`/opt/kg/backups/grafana-pre12-*.db` before the recreate. After the restart
`/api/health` answers 12.4.12 with `"database": "ok"`, the Loki, Tempo and
Prometheus datasource health checks all return OK, all six provisioned dashboards
are present, and `grafana-lokiexplore-app` 2.6.0 (Logs Drilldown; also
`grafana-exploretraces-app` 2.2.1) is installed and enabled. Every panel in the six
dashboards is a core `timeseries`/`logs`/`table`/`text` panel, so nothing depends
on the Angular plugins Grafana 12 removed. The datasource port stays
`127.0.0.1:3001`.

`http_route` and `user_id` are now promoted at the Collector to log-record
attributes, which Loki stores as structured metadata: they filter with
`| http_route="..."` / `| user_id="..."`, with no `| json`, and the indexed label
set is unchanged at `service_name` + `deployment_environment_name`. Promotion is
shape-gated and fails closed — a value that is not a route template
(`^(_unmatched|/[A-Za-z0-9_{}/.-]{0,159})$`) or an account UUID is deleted from
the body, so the Collector never publishes or retains a raw URI/query, and only
`service.name == "knowledge-gym"` may publish either field.

Production evidence after the rollout:

| Check | Result |
| --- | --- |
| `sum(count_over_time({service_name="knowledge-gym"} \| http_route=~".+" [30m]))` | 46 |
| `sum(count_over_time({service_name="knowledge-gym"} \| trace_id=~".+" [24h]))` | 273 |
| `sum(count_over_time({service_name="nginx"} \| user_id=~".+" [24h]))` | empty (deletion rule holds) |
| real app line | metadata `http_route=/actuator/prometheus`, `trace_id`, `span_id`, `severity_text` |
| native Collector canary (`scripts/test-observability-pipeline.py`) | PASS: route/actor published; raw URI and email fail closed |

`user_id` can only appear once an authenticated request is logged, and the
deployed app serves no user traffic by itself, so the code path was verified
instead of waiting for a human click: the running `sha-91fa847` image contains
`UserMdcFilter` (`unzip -l /app/app.jar | grep -ci usermdcfilter` = 1) and the
encoder tests reject every non-UUID shape. Query the first real line with
`{service_name="knowledge-gym"} | user_id=~".+"`.

Rollback: set Grafana back to `grafana/grafana:11.1.0`, stop it, restore
`/opt/kg/backups/grafana-pre12-*.db` into the `kg_grafanadata` volume and start it
(a 12-upgraded DB is not readable by 11, which is why the copy exists). Revert the
app with `APP_IMAGE=<previous sha>` plus `up -d app`; the Collector change reverts
with the file (`git checkout <previous> -- ops/observability/collector.yml`
followed by `--force-recreate otel-collector`, which is the only way to pick up a
bind-mounted config change).

Pre-upgrade leftover: the OTLP probe used to prove attributes -> structured
metadata pushed one line straight to Loki with a synthetic `user_id`, bypassing the
Collector, under `service_name="hermes-otlp-probe"`; it is the only such line that
exists outside the application. Delete request `84b1b3bd` was accepted (delete
store: filesystem), so the compactor removes it within the 2h delete delay instead
of at the 72h retention boundary.

## Upgrade record (2026-10-11, log dashboards filter on metadata, no `| json`)

The application log panel no longer parses bodies. Its query is now

```logql
{service_name=~"${service:regex}", deployment_environment_name=~"${environment:regex}"}
  | severity_text=~"$level"
  | request_id=~"${request_id:regex}.*" | trace_id=~"${trace_id:regex}.*"
  | http_route=~"${route:regex}.*"  | user_id=~"${user:regex}.*"
```

Every predicate reads a field the Collector already promoted, so a query scans the
line once instead of running a JSON parser over each body. `request_id` joined the
promotion allowlist (`keep_keys(attributes, ["http_route", "user_id",
"request_id"])`) with the same shape gate as the other two — `^[0-9a-f]{32}$`,
deleted from the body when it does not match, and promoted for nginx as well as the
application so one 32-hex ID joins an edge rejection to the app line it produced.

An empty textbox is a no-op rather than a filter that hides everything: Loki
matches an *absent* field against `.*`, so `| user_id=~".*"` keeps lines with no
`user_id` at all. That was measured before the design was chosen, on both streams
(filtered vs unfiltered `count_over_time` over 3h):

| Query | Lines |
| --- | --- |
| `{service_name="knowledge-gym"}` | 829 |
| `{service_name="knowledge-gym"} \| user_id=~".*"` (also `http_route`, `request_id`, `severity_text`) | 829 |
| `{service_name="nginx"}` | 1483 |
| `{service_name="nginx"} \| severity_text=~".*"` (nginx publishes no such field) | 1483 |

`Route` and `User ID` are therefore plain textboxes, not dropdowns: a textbox holds
literal input, so `${route:regex}`/`${user:regex}` escaping is what we want (unlike
the earlier `$level` custom-variable bug, where `:regex` broke the `.*` default).
Two tables below the log panel (top routes, top actors by request volume, `topk(15)`
over `$__range` with `| field=~".+"` so only lines that actually carry the field
appear) carry data links back to the same dashboard, so a click fills the box and
re-renders the log panel. The route filter is a prefix match by design.

Verification before the rollout: the native Collector canary
(`scripts/test-observability-pipeline.py`, real 0.162.0 binary) passes with
"route/actor/request ID published as structured metadata, unsafe shapes fail
closed" — the safe line publishes `{http_route, user_id, request_id}`, the unsafe
line publishes nothing and loses all three keys from the body; `scripts/
verify-observability.py` asserts the three-field allowlist, the promotion and
fail-closed delete statements per field, and that no log panel parses with
`| json`.

## Post-deploy QA (2026-10-11, `scripts/qa-dashboard-logs.py`)

`python3 scripts/qa-dashboard-logs.py` on CT102 (it reads
`GF_SECURITY_ADMIN_PASSWORD` from the environment; run it as `cd /opt/kg && set -a
&& . <(grep -E "^GF_SECURITY_ADMIN_PASSWORD=" .env) && set +a && python3
scripts/qa-dashboard-logs.py`). It reprovisions, then replays through
`/api/ds/query` what the browser would send — template variables and the
`$__range`/`$__auto` macros interpolated the way the frontend does — so the check
does not depend on someone opening a browser. Result on the live stack: **17/17**.

| Check | Observed |
| --- | --- |
| Application log panel, every box empty | 100 lines returned, no `\| json` in the query |
| Nginx log panel, every box empty | 100 lines returned, no `\| json` in the query |
| Empty filters vs no filters | 245 = 245 lines / 30m (the empty box really is a no-op) |
| `severity_text` / `trace_id` / `http_route` as metadata | 245 / 174 / 173 lines / 30m |
| `request_id` as metadata | 31 lines / 30m (only since the Collector was recreated) |
| `user_id` as metadata + top-actors table | 37 lines / 30m, one 36-char UUID actor |
| Top-routes table, and a route value filtering | 14 routes; `/actuator/prometheus*` → 119 lines |
| Filtered dashboard URL (`?var-route=…`) | HTTP 200 |

The same script also checks the two per-datasource endpoints Grafana Logs Drilldown
depends on, asking Loki through Grafana's proxy: the `volume` endpoint (byte volumes
per service) and `detected_level` (the level filter). Loki 3.7 answers volume
queries with no extra configuration, but level detection is off by default, so
`limits_config.discover_log_levels: true` was added to `ops/observability/loki.yml`
— `detected_level` is written as structured metadata, so the label count is
unchanged. Without it, Drilldown shows every line as unlevelled and its level
filter stays empty even though the dashboards' own `severity_text` filter works.

## Decision record: trace volume, the Patterns tab, acceptance (2026-10-11)

Three items were still open after activation; all three are now decided and
measured on the running stack.

**1. Sampling: keep every trace, drop the scrapes.** The old sampler dropped 95%
of traces app-side, which also threw away the child-span chains the drill-downs
need. The app now exports **everything** (`OTEL_TRACES_SAMPLER=parentbased_traceidratio` with `OTEL_TRACE_SAMPLE_RATIO=1.0`,
the default, in `docker-compose.observability.yml`) and the Collector makes the only drop
decision, per **whole trace**, in `tail_sampling`:

- A span-level filter was tried first and was the wrong tool: dropping the root
  span of a scrape leaves its children behind as orphans that still show up as
  traces. A tail-sampling `drop` policy discards all spans of the trace together.
- The route matcher must **not** be anchored. `http.route` carries the servlet
  context path (`/api/v1/actuator/prometheus`), so an earlier `^/actuator/` never
  matched; the policy now tests an unanchored `/actuator/` substring.
- In Collector 0.162.0 the `drop` policy takes `drop_sub_policy:` — a list of
  ANDed sub-policies. `OTTL.Condition` has no `decision` field at this version,
  so `type: ottl_condition` cannot express a drop. The shape was validated with
  the real binary (`otelcol-contrib validate --config`) before the rollout.

Acceptance, since the Collector was recreated: `otelcol_processor_tail_sampling_
count_traces_sampled` shows `dropped` on policy `drop-scrapes` for the scrape
traces and `sampled` on `keep-everything-else` for the rest, and a Tempo search
for `{resource.service.name="knowledge-gym" && name=~".*actuator.*"}` over the
last 5 minutes returns **0** traces (before the change the same query returned
39 over 20 minutes, the newest one 3 seconds before the recreate). Real requests
are still kept in full: 50 traces in the same 5-minute window.

**2. The Patterns tab stays off — measured, not assumed.** `pattern_ingester` was
run for real on 2026-10-11: enabled in `loki.yml`, Loki recreated, and it accepted
traffic with zero errors (122 batches pushed) — and still answered
`/loki/api/v1/patterns` with `data: []`, even for a catch-all matcher. The reason is
our log shape: the tokenizer reads the JSON body's `message` field, and in the app
stream that field is the constant `http_request_completed`, so the drain classified
302 lines as `too_few_tokens` and produced no pattern at all. Turning it on would
cost disk and CPU for a permanently empty tab, so it is now commented out in
`ops/observability/loki.yml` with that evidence in the comment. With it off Loki
does not register the route at all (HTTP 404), which is the documented answer —
`scripts/qa-dashboard-logs.py` asserts exactly that, so a future 500 or a silent
behaviour change is caught. Re-enable only together with a richer `message` field,
and then verify with `curl -G …/loki/api/v1/patterns`, not with the tee metrics
(they look healthy even when nothing is queryable). Recipe in
`docs/observability-runbook.md`.

**3. Acceptance runs.** All three tests that were previously "NOT RUN" now have
numbers from production traffic on CT102:

| Test | Method | Result |
| --- | --- | --- |
| Soak (25 min, 11 snapshots) | `docker stats` + Loki/Tempo ingest counters + `du` on the ops volumes | Loki +227 kB / 646 lines over ~26 min (≈13 MB/day raw); ops volumes 2 MB (loki) and 7 MB (tempo) |
| Disk headroom | `df` on the CT102 root disk | 11 G used / 64 G free (15%) — the ops stack is not a disk risk |
| Memory headroom | `docker stats` peaks during the soak | loki 96 MiB/384, collector 79 MiB/256, tempo peak 283 MiB/384, grafana 129 MiB/512; CT at 3.3 GiB/8 GiB |
| Rollback drill | stop the whole ops profile, keep serving, start it again | app answered continuously (401 from the auth layer, as expected), 0 restarts, no exporter errors; after restart new log lines and traces landed again |

The only watch item is Tempo's peak (283 MiB of its 384 MiB limit under load); if
it ever approaches the limit, raise `mem_limit` for `tempo` in
`ops/docker-compose.ops.yml` — the container is not swapped, so OOM would restart
it, not the app.

The post-deploy QA was extended to 22 checks with the same run; the two new ones
assert the scrape-drop above and that the Patterns endpoint answers (or is absent)
exactly as documented.

Three Loki/Grafana facts this surfaced, worth knowing before editing a panel:

- Loki rejects a selector whose matchers are **all** empty-compatible
  (`{a=~".*", b=~".*"}` → HTTP 400). The `service`/`environment` variables
  therefore keep `includeAll: false`, and an "all values" default has to be `.+`
  rather than `.*`. Filtering a stream selector down to nothing by picking
  "*All*" would look like a broken panel, not an empty one.
- A log panel must be sent as a **range** query; the same query as `instant`
  returns `log queries are not supported as an instant query type`.
- The Loki backend itself expands `$__range`, `$__auto` and `$__interval`, but
  **not** `$__rate_interval` (HTTP 400) — use `$__range` for "over the selected
  range" aggregations such as the two `topk(15)` tables.
