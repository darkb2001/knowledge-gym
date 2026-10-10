# Logging and redaction policy

## Allowed application envelope

Console is one UTF-8 JSON object per line, UTC timestamp (Z), fixed service/
environment, level, logger and reviewed constant message template. Within a
request: trace_id, span_id, request_id, HTTP method, route template, status and
duration_ms. Outside a request those HTTP fields are absent. Without an agent/
valid context trace IDs are absent, not fabricated.

MDC is allowlisted; arbitrary key-value pairs are not serialized.
Logging does not read request/response bodies, query strings or security headers.
Route uses BEST_MATCHING_PATTERN_ATTRIBUTE; unknown paths are _unmatched.
Do not concatenate user data into a message literal.

Dynamic string/object arguments become [REDACTED] before formatting. Primitive
number/boolean diagnostics are preserved, with additional masking of 4–8 digit
values to protect common OTPs. Emails, IP-shaped values, URLs, bearer values,
long opaque values and sensitive key assignments in literal messages are masked
as defense in depth. Regex alone cannot identify an arbitrary plain password.

Third-party logger messages become library_event: libraries may concatenate
HTTP/SQL/SMTP payloads before reaching SLF4J. Class, level and sanitized stack
frames remain useful. This deliberately reduces startup/client diagnostics.

Throwable messages are never emitted, including nested/suppressed messages.
Exception classes, stack frames and causal structure are retained with global
128-frame, 16-exception, depth-8 bounds; frames are capped at 256 characters,
templates at 4096. Truncation is explicit. Encoding errors emit a fixed fallback
record, never the original event.

## Never emit

Passwords, password hashes, access/refresh JWTs, OAuth authorization codes,
PKCE verifier, cookie/Authorization values, API/SMTP/storage/cron secrets,
OTP/reset/verification codes, raw emails, request/response bodies, SQL binds,
Redis command payloads, Kafka message keys/bodies, AI prompts/responses or
unredacted downstream exception messages.

No HTTP wire logging or SQL/bind logging in production. Do not turn on JavaMail
session debug, JDBC driver debug or body-logging interceptors. Console print/
native agent diagnostics bypass the encoder; code review must forbid printing
sensitive objects. The agent's diagnostics are disabled in the overlay.
Security audit records stored in PostgreSQL remain separate and are not
exported as application log content.

## Collector boundary

Only selected Docker service labels and structured records are accepted.
Unlabelled/unstructured/unknown services are dropped quietly, without diagnostic
payload echo. Historical logs are not harvested on first start (start_at=end).
Body/resource fields are allowlisted; infrastructure free-form messages,
stack traces, logger and routes are removed/replaced. IDs never become labels.

Trace resource/span attributes are allowlisted. Raw URLs/queries, SQL statements,
bodies, user/client identifiers, status messages and events are removed.
Scopes lose attributes; stored trace_state is cleared (W3C propagation remains).
Linked spans are rejected; the app agent disables links.
Do not change error_mode to ignore to make a failing redaction transform pass.
A transform/filter error must reject telemetry rather than forward raw data.

Telemetry network is private but not a cryptographic trust boundary. These
allowlists are for this app's trusted instrumentation, not a general-purpose
PII classifier for arbitrary senders. Changing an allowed field's meaning or
adding a sender requires another privacy review.

## Nginx and infrastructure

Access logs contain generated request ID, upstream trace ID, method, numeric
status/upstream status/duration/byte counts. No raw URI/query/IP/User-Agent/
header/body in the shipped stream.
Native Nginx error lines are never shipped: they echo request URLs and the
collector's JSON gate drops them. They are kept on stderr (`error_log
/dev/stderr crit;`) so worker/upstream/TLS failures stay visible in local
`docker logs kg-nginx-1`; redirecting them to /dev/null hides real outages.
Detailed edge TLS/upstream diagnostics still rely on access 4xx/5xx plus
`status`/`upstream_status`/`upstream_duration_seconds`, so do not raise the
error level or add raw error fields to the shipped stream without a reviewed,
bounded handling plan.

Only opted-in JSON logs from Prometheus/Grafana/Alertmanager are collected.
Raw logs from PostgreSQL/Redis/Kafka/Elasticsearch/Garage are intentionally not
centralized until separately reviewed. Those existing services may still retain
their own native Docker logs; this change does not retroactively sanitize them.

## Levels, access and retention

LOG_LEVEL_APP / LOG_LEVEL_ROOT configure production levels without rebuilding,
but changing container environment needs an approved app recreation.
Console logging is asynchronous: queue 256, neverBlock=true, flush at most 1s;
Docker opted-in buffers are non-blocking and bounded at 1 MiB. Full buffers drop
logs, including error logs, instead of blocking the application. There is no
lossless-delivery or dedicated per-app dropped-log counter guarantee.
The public Actuator logger-management endpoint is NOT exposed.
In-process level changes are unit-tested; no new admin logging API is introduced.

Loki 72h, Tempo 24h; Docker files rotate 10 MiB × 3 for opted-in services.
Local raw Docker retention is distinct from backend retention. Never paste
production log exports into issues or CI. Grant Grafana organization access only
to operators; a Viewer can query all organization datasources in OSS Grafana.

Tests use synthetic sentinels, never production credentials. Run encoder/filter/
exemplar tests and native pipeline canary after any logging or allowlist change.
