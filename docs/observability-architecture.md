# Knowledge Gym observability architecture

## Status and scope

Repository implementation, **not production certification**. No production SSH,
restart, deployment, commit or push was performed. Container/Grafana acceptance
must pass on staging before activation. See [deployment](observability-deployment.md)
and [runbook](observability-runbook.md).

The supplied host baseline is Proxmox LXC kg-be, 192.168.1.15, 4 vCPU, 8 GiB,
approximately 80 GiB on one NVMe, roughly 5 GiB RAM and 67 GiB disk free.
These are operator-provided measurements, not measurements taken by this change.

Repository baseline: Spring Boot 4.1.1 / Java 21; existing Prometheus 2.53.0,
Grafana 11.1.0, Alertmanager 0.27.0, Docker json-file 10 MiB × 3.
The existing application Compose memory caps total 6112 MiB; existing
Alertmanager adds 192 MiB. Current usage is not the sum of configured caps.

## Data flow and isolation

```text
Browser → Cloudflare/Nginx → Spring MVC → JDBC/Redis/HTTP/Elasticsearch
                                  └→ scheduled outbox → Kafka → consumer

console JSON → Docker json-file → Collector filelog → Loki local TSDB
Java agent W3C context → Collector OTLP → Tempo local blocks
Prometheus → existing app/nginx metrics + Collector/Loki/Tempo metrics
Grafana authenticated proxy → Prometheus / Loki / Tempo
Prometheus rules → existing Alertmanager
```

Ops project kgops owns Collector, Loki, Tempo and their volumes. Application
project kg remains separate, so app --remove-orphans cannot delete ops services.
The explicit observability profile/overlay is not automatically activated by CI.

kgops_telemetry is internal. Only app, Prometheus and Grafana additionally join
it. Collector/backends have no host-published ports and no public Nginx route.
Grafana is the access boundary; Loki/Tempo are single-tenant without application
authentication. Any container joined to this network is a trusted principal.
This does not provide tenant isolation against a compromised app/host.

Collector runs as root solely to read root-owned log files, with all capabilities
dropped, read-only root filesystem and no Docker socket/API. Only Docker's
containers log directory is bind-mounted read-only. Its position state is
persistent. Loki/Tempo run UID 10001 after a bounded, network-less volume-init job.

## Instrumentation and correlation

Agent 2.32.0 is included with a fixed SHA-256 in the application image but inert
unless the overlay adds -javaagent. Do not install a second tracing SDK/starter.
Metrics remain Micrometer/Actuator/Prometheus; OTLP metrics/log export is disabled
to avoid duplicate signals.

Agent instrumentation provides servlet/Spring HTTP server context, supported
JDK/Spring HTTP clients, JDBC beneath JPA, Lettuce/Redis, Kafka and Spring
scheduled execution. The Elasticsearch Java client already uses OTel API; its
native spans and HTTP transport must be verified on this exact client version.
Library support is not proof that every deployed route is traced.

Nginx overwrites incoming X-Request-ID with its generated 32-hex ID. The filter
validates direct internal IDs, restores MDC in finally and logs a route template,
not the raw URI. It returns X-Request-ID and, when a valid span exists, X-Trace-ID.
Nginx reads the upstream trace ID for correlation. Invalid traceparent is
discarded by W3C parsing; tracestate is propagated between services but stripped
before storage to avoid retaining caller-supplied free-form state.
IDs are diagnostics, never
credentials or identity assertions. Existing CORS/authentication policy is unchanged.

Single-record Kafka producer/consumer propagation is tested with an actual
embedded Kafka broker and the checksum-pinned agent. A scheduled outbox relay
creates a new trace: original HTTP context is **not persisted in outbox rows**.
Batch fan-in linkage is deliberately unavailable: agent span-link count is zero;
Collector rejects spans containing any links fail-closed. Span events are
removed by filterprocessor; [] is not a valid replacement for typed pdata slices.
This conservative privacy trade-off must be accepted before deployment.
It can create orphaned spans if another instrumentation source emits linked spans.

## Storage, limits and sampling

| Component | Cap | CPU cap | Persistence / retention |
|---|---:|---:|---|
| Collector | 256 MiB | 0.50 | file offsets only |
| Loki | 384 MiB | 0.75 | local TSDB/chunks/WAL, 72h |
| Tempo | 384 MiB | 0.75 | local blocks/WAL, 24h |
| Volume init | 32 MiB transient | 0.10 | directory ownership only |

Steady-state new caps: 1024 MiB. Combined existing app + Alertmanager + new caps:
7328 MiB, leaving about 864 MiB under 8 GiB **before host/kernel overhead**.
App heap changes from 65% to 55% inside the existing 1024 MiB cap.
Measure RSS/metaspace/GC/latency before approving these limits.

Root trace sampling is parent-based 5%; sampled remote parents are honoured.
This is probabilistic sampling, not a traffic cap, and an internet caller can
request a sampled parent. Existing edge rate limits remain relevant. Logs are
not probabilistically sampled; privacy/schema filters and full buffers can
still drop them.

Console logging uses a 256-event AsyncAppender with neverBlock=true and a
1s shutdown flush; full queues drop logs instead of blocking app threads.
Docker opted-in log drivers also use bounded 1 MiB non-blocking buffers.
Those drops do not have dedicated application counters in this implementation.
Agent queue: 1024 spans, batch 128, 2s schedule, 3s export timeout.
Collector memory limiter: 192 MiB / 32 MiB spike; log batch maximum 512,
trace batch maximum 256; each exporter queue 64 requests, one worker, 3s timeout,
retry deadline 60s. Queues are memory-only and not durable. Full queues,
memory pressure, permanent rejection and process crashes can lose telemetry.
Backend failure must not become application-readiness failure.

Retention is not a hard disk quota. Initial soft budget: 3 GiB Loki + 2 GiB
Tempo + 1 GiB WAL/compaction headroom; enforce monitoring and revise from
observed daily growth. No filesystem hard limit is introduced. See runbook.
Do not migrate Garage or use Elasticsearch as a log backend.

## Grafana and security

Existing JVM/HTTP and Nginx metrics dashboards are preserved. New views:
Application Overview & Logs, Nginx Logs, Distributed Traces, Pipeline Health.
Stream labels are only service_name and deployment_environment_name.
Level/request/trace IDs are parsed at query time, not indexed labels.
Request/trace textbox filters are prefixes; full-length IDs effectively match
one ID. Tempo → logs uses trace ID with a ±2m window. Metrics exemplars point
to Tempo. HTTP histograms use finite SLO buckets; p95 is approximate.

Grafana anonymous access and sign-up are disabled. No new public port is added.
OSS Grafana viewers can query organization datasources: folder permissions are
not datasource row-level security. Restrict organization membership to operators
and verify the existing tunnel/Access policy; this change does not certify it.
Production Grafana 11.1.0 and other pre-existing versions need a separate
security-upgrade review, not a claim that this stack is fully patched.

## Evidence and remaining acceptance

Source contracts, redaction/context/exemplar tests, real Kafka propagation and
native Collector synthetic pipeline tests are provided. The native canary checks
events removal, linked-span rejection, backend 503/recovery and offset restart.
It uses local HTTP JSON test exporters, not production Loki/Tempo.
Full Gradle build was attempted and failed in Docker-backed integration tests
because the local Docker daemon is unavailable. Actual backends, healthcheck
images, dashboards, sustained queue saturation, disk pressure and host resource
impact remain staging gates.

Upstream: [agent libraries](https://opentelemetry.io/docs/zero-code/java/agent/supported-libraries/),
[OTLP to Loki](https://grafana.com/docs/loki/latest/send-data/otel/),
[filterprocessor](https://github.com/open-telemetry/opentelemetry-collector-contrib/tree/v0.162.0/processor/filterprocessor).
