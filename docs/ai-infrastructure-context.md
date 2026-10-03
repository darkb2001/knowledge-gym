# Knowledge Gym — AI Infrastructure Context

Use this document as context when reviewing or sizing the infrastructure for Knowledge Gym. Treat repository configuration as evidence of what is implemented. Treat statements from planning documents as proposals until deployment is independently confirmed. Do not assume the design document describes the live server exactly.

## Project summary

Knowledge Gym is a Java 21 / Spring Boot 3.2 modular backend (Gradle, Clean Architecture/DDD) with a Next.js frontend. The backend uses PostgreSQL, Redis, Kafka, Elasticsearch and S3-compatible object storage. Docker Compose defines the local/self-hosted service stack. The Docker Compose file does not currently define a frontend container.

## Evidence and deployment status

| Area | Repository evidence | Status to assume |
|---|---|---|
| Backend container | Root `Dockerfile`, built as a Spring Boot executable JAR on Java 21 | Implemented in repo |
| Local/self-hosted services | `docker-compose.yml` defines nine containers | Compose configuration exists; this alone does not prove they are running in production |
| Proxmox host and LXC plan | Operator reports live PVE API measurements and a proposed CT 102 spec for node `darkb` | Treat the figures below as operator-reported live measurements (not independently verified here); CT 102 and host sysctl changes are pending execution/confirmation |
| AWS Lambda + EventBridge | Mentioned and illustrated in `docs/11-cloud-free-tier.md`; no Terraform directory or Lambda source is present | Planned/documented, not implemented in this repo |
| Frontend hosting | Firebase Hosting / GitHub Pages are discussed in docs | Proposed; verify actual hosting and whether Next.js needs SSR |
| Production mail | Mailu on a separate LXC is described in docs | Proposed; no Mailu deployment config in this repo |
| Proxmox Backup Server | PBS is described as an optional backup LXC | Proposed; verify live setup |
| Database offsite backup | `scripts/backup-db.sh` contains pg_dump/restic-to-B2 workflow | Script exists; verify cron, credentials, B2 repository and restore procedure |
| Monitoring | Prometheus and Grafana services are defined in Compose | Configured in repo; verify live scrape targets, retention and alerting |

## Services defined by Docker Compose

All nine services share the Compose host/LXC unless the deployment is changed.

| Service | Image / runtime | Role | Persistent data / notable settings |
|---|---|---|---|
| `nginx` | `nginx:1.27-alpine` | Reverse proxy, ports 80 and 443 | Nginx config mounted read-only from `infra/nginx.conf` |
| `app` | Locally built Java 21 JRE image | Spring Boot backend, port 8080 | Mounts `../docs` read-only; container runs as unprivileged `app` user. Compose enables collector and Kafka integration; AI writer defaults off |
| `db` | `postgres:16-alpine` | Primary relational database | Named volume `pgdata`; local Compose credentials are `postgres` / `postgres` and must not be reused for production |
| `redis` | `redis:7-alpine` | Cache and token/session-related support | Named volume `redis-data`; configured maxmemory 128 MB, `allkeys-lru`, AOF enabled |
| `kafka` | `apache/kafka:3.8.1` | Event backbone, single-node KRaft broker/controller | Named volume `kafkadata`; replication factor 1, so this is not HA |
| `elasticsearch` | `elasticsearch:9.1.5` | Search | Named volume `esdata`; single-node, security disabled for local Compose; JVM heap explicitly 512 MB (`-Xms512m -Xmx512m`) |
| `garage` | `dxflrs/garage:v2.4.1` | S3-compatible object storage | Named volume `garage-data`; single node; S3 API 3900, admin/metrics 3901, web 3902 |
| `prometheus` | `prom/prometheus:v2.53.0` | Metrics collection | Named volume `promdata`; scrape config from `infra/prometheus.yml` |
| `grafana` | `grafana/grafana:11.1.0` | Dashboards | Named volume `grafanadata`; port 3001 on host |

The current Compose file publishes PostgreSQL 5432, Redis 6379, Kafka 9092, Elasticsearch 9200 and Garage 3900–3902 (and Grafana 3001). For the proposed CT 102 deployment, remove host-published ports for these services; keep backend-to-service traffic on the private Compose network and expose only Nginx on port 80. Confirm the production Compose changes before treating this as implemented. The backend connects to Compose service DNS names (`db`, `redis`, `kafka`, `elasticsearch`, `garage`).

## Resource facts versus estimates

### Explicitly configured in the repo

- Redis maxmemory: **128 MB**.
- Elasticsearch JVM heap: **512 MB**. Elasticsearch also needs memory outside the Java heap for native memory and the OS page cache.
- The Compose file has **no CPU or memory limits** for any container.
- PostgreSQL, Kafka, Garage, Prometheus, Grafana, Nginx and the backend have no explicit container-level RAM/CPU limits in Compose.
- The Dockerfile does not set JVM heap or container CPU settings for the backend.
- Prometheus retention is not evident from the Compose definition; inspect `infra/prometheus.yml` and deployment flags before sizing disk.

### Live PVE report and Knowledge Gym CT 102 plan

The operator clarifies that the existing **CT 100 and CT 101 have 6 GB RAM allocated in total**, while their current observed use is about **2 GB and change**. This is distinct from the earlier ambiguous wording that the host had 6 GB used. The operator additionally reports a live PVE API snapshot with node memory use around **2.70 GB currently / 2.74 GB 30-day peak**, and post-allocation budget **2 + 4 + 8 = 14 GB of 15.54 GB**. These are operator-reported live measurements, not independently verified in this repository. Preserve the measurement time, API fields, and whether figures mean allocated limits, actual usage, or node usage when refreshing them; do not conflate CT allocation with actual consumption.

The operator's **final target** is **CT 102 (`kg-be`)**: Ubuntu 24.04 standard template `local:vztmpl/ubuntu-24.04-standard_24.04-2_amd64.tar.zst`, 4 vCPU, 8 GB RAM, 2 GB swap, 80 GB `local-lvm` thin disk (grow online when usage exceeds 70%), `nesting=1,keyctl=1`, unprivileged Ubuntu container, `onboot=1`, and `eth0` on `vmbr0` with `192.168.1.15/24`, gateway `192.168.1.1`. The operator reports VMID 102 is available. This is the final approved target in planning, **not confirmation that CT 102 has been created**. Keep all nine services on-premises; the hybrid decision adds offsite backup and optional offsite metrics, and does not reduce the 8 GB / 80 GB allocation. Estimated container ceiling is about 5.98 GB plus about 0.8 GB for OS/Docker, leaving roughly 1.2 GB headroom; this is an estimate, not a production load test.

The operator explicitly supersedes the older `docs/11-cloud-free-tier.md` proposal of 4 vCPU / 12 GB / 100 GB with this 4 vCPU / 8 GB / 80 GB final target. Mailu (1–1.5 GB) and PBS (2 GB) are not part of this deployment; confirm live status before including them in capacity totals.

### Host sysctl request (pending)

Before starting Elasticsearch, the operator requires these host-level settings persisted in `/etc/sysctl.d/99-kg.conf`: `vm.max_map_count=262144`, `fs.file-max=2097152`, `vm.swappiness=10`, and `net.core.somaxconn=4096`. These affect the PVE host, not just CT 102. They have **not been applied**; host console access and operator approval are required before making this side-effect. No CT restart is expected according to the operator.

### Proposed Compose memory/resource and exposure changes

The operator proposes per-container memory limits: nginx 96 MB, app 1.25 GB, PostgreSQL 640 MB, Redis 192 MB, Kafka 1.25 GB, Elasticsearch 1.25 GB, Garage 384 MB, Prometheus 512 MB and Grafana 320 MB (about 5.98 GB total, depending on units/rounding). Additional runtime settings proposed: app JVM `-XX:MaxRAMPercentage=65`; PostgreSQL `shared_buffers=256MB`; Redis `maxmemory 128mb`, `allkeys-lru`, AOF; Kafka `KAFKA_HEAP_OPTS=-Xms512m -Xmx1g`; Elasticsearch heap `-Xms512m -Xmx512m` and `bootstrap.memory_lock=false`; Prometheus retention 15 days. These are proposed deployment settings, not yet confirmed in Compose. Validate Docker Compose limit semantics and leave enough room for process overhead, native memory and page cache; a memory limit is not a guarantee that the workload fits safely.

Production credentials must not use the repository's local `postgres` / `postgres` defaults; use uncommitted secret configuration (for example, a protected `.env` file). Keep PostgreSQL, Redis, Kafka, Elasticsearch, Garage and Grafana off public host ports; expose only Nginx :80 on the proposed CT. Confirm the Compose changes and firewall/network behavior before go-live.

### Public API entrypoint and application security (final choice, pending deployment)

The frontend is hosted outside this CT and needs public API access. The operator chose Cloudflare Tunnel in CT 102, routing `api.darkb-tech.io.vn` to `http://localhost:80`; this avoids opening an inbound port and does not depend on CT 100. The operator identifies CT 100's NPM as shared with the LLM gateway, so API/DDoS traffic there would expand the blast radius; isolating ingress in CT 102 is the chosen mitigation. Fallback only: add a Proxy Host in NPM on CT 100 with Websockets and a Let's Encrypt certificate (operator expects this live change not to restart containers, but CT 100 remains a shared failure point). Tunnel and fallback are not yet confirmed deployed. Keep Elasticsearch, Kafka and Grafana inaccessible from the public internet. At application/proxy level, configure a precise CORS allowlist for the frontend domain (never `*`), rate limiting in Nginx, and real authentication (JWT/session); verify these are implemented before go-live.

### Offsite backup, metrics, capacity and go-live checks (final hybrid plan)

The 80 GB CT disk remains required because all nine services stay local. Operator estimates: images 6–8 GB, PostgreSQL 2–8 GB, Elasticsearch 10–20 GB, Kafka 5–15 GB, Garage 5–20 GB, Prometheus 5–10 GB, logs 2–5 GB. These ranges overlap and are not a guaranteed capacity calculation; set retention/cleanup policies and monitor actual growth. Operator's thresholds: investigate `local-lvm` pool usage above 75%; treat above 85% as dangerous because a full thin pool can affect every CT. One NVMe and no RAID means disk failure is a shared loss event across all three CTs. PVE snapshots are not offsite backup.

Keep Garage running locally. Offsite backup is the hybrid data copy: priority is PostgreSQL dumps, Garage objects, `.env`/secrets and Compose/configuration; Elasticsearch and Kafka are considered rebuildable and are excluded from the initial backup scope. Preferred low-change route is the existing `scripts/backup-db.sh` pg_dump + restic workflow to Backblaze B2. Cloudflare R2 is an alternative; its reported 10 GB free tier may be insufficient for large Garage objects, so use paid B2 or limit offsite scope to PostgreSQL and config when needed. Protect secrets in the backup repository and verify restore access. Scheduled backup, credentials, offsite repository and restore are **not yet verified**.

Optional but recommended offsite metrics: keep local Prometheus/Grafana and add Prometheus `remote_write` to Grafana Cloud Free (operator-reported limits: 10k series, 3 users). This preserves monitoring history if the PVE host fails; it does not replace local monitoring. Verify current plan limits before relying on them.

Operator-reported capacity plan is 14 GB allocated of 15.54 GB total (`2 + 4 + 8`), with projected total use around 7 GB; PVE API snapshot reports 2.70 GB current / 2.74 GB 30-day peak before CT 102. Preserve the distinction between allocation, projected use and measured node use. Action thresholds: node RAM above 80% for 15 minutes, increasing KG cgroup `oom_kill`, Elasticsearch heap above 75% after full GC, or monotonically increasing Kafka lag. Reported upgrade option is 2×16 GB DDR4 SODIMMs to 32 GB (estimate 600–900k); interim option is reducing CT 101 from 4 GB to 3 GB. Re-measure before changing allocations. Mailu and PBS are excluded from this capacity plan.

Before go-live, verify host sysctl, create CT 102, install Docker and Compose, create a non-root operator account and configure SSH key authentication (the operator notes `/root/.ssh/id_ed25519` is unused), deploy Compose with limits and no published internal-service ports, then check: `docker ps` 9/9 healthy/up, `curl localhost:80` returns 200, Elasticsearch health green, Kafka produce/consume, Garage bucket listing, Grafana querying Prometheus, Cloudflare Tunnel and public HTTPS API, offsite backup, and one successful restore. These are acceptance checks, not completed test results.

## Application workload and scaling considerations

- Spring Boot backend modules: `kg-core`, `kg-infrastructure`, `kg-presentation`, and `kg-agent`.
- PostgreSQL is the system of record and runs Flyway migrations at application startup.
- Redis has an explicit 128 MB cap; assess eviction and persistence needs against actual cache/session use.
- Kafka is currently a single broker with replication factor 1. It has no redundancy if the node or volume fails.
- Elasticsearch is single-node and its security is disabled in Compose. Treat that Compose setup as private-network/dev-oriented unless secured at the deployment layer.
- Elasticsearch now backs global search (`GET /search`): a single non-dated index `knowledge-gym-search` fed by `search_outbox` (V023 triggers) through `SearchIndexRelay`, with automatic fallback to the PostgreSQL `tsvector` query when the cluster is unreachable. Retention is document-level (the outbox pruner); there is no ILM/rollover, so `scripts/es-retention.sh` (which deletes `knowledge-gym-YYYY.MM.DD` indices) is inert by design until rollover is adopted.
- Garage is configured as a single node. It is not a replicated object-storage cluster in this Compose setup.
- `CollectorScheduler` and blog writer scheduling run inside the Spring application when enabled. Avoid also scheduling equivalent work externally unless duplicate execution is made safe. AWS Lambda cron jobs shown in docs do not have corresponding handlers/endpoints in this repo.
- Blog writer can call an external OpenAI API when enabled; include request/token budgets and API cost controls in capacity and operational planning.
- Frontend is in `kg-frontend`; choose hosting based on whether its Next.js routes require server-side rendering. Static hosting is only appropriate if the app is built for static output or separated accordingly.

## Backups and failure domains

- Compose named volumes hold primary DB, Redis, Kafka, Elasticsearch, Garage, Prometheus and Grafana data. Volume persistence is not itself a backup.
- `scripts/backup-db.sh` describes PostgreSQL dump plus restic to Backblaze B2. Confirm it is installed with valid secrets, scheduled, monitored, and periodically restored.
- Docs propose Proxmox Backup Server snapshots as an infrastructure backup layer. Confirm actual PBS availability and datastore capacity.
- A single Proxmox host/LXC running all nine services is one CPU, memory, disk and host failure domain. Compose single-node Kafka, Elasticsearch and Garage do not provide high availability.

## Guidance for an AI doing capacity planning

1. First label every input as **measured live**, **configured in repo**, or **documented proposal**. Do not conflate these.
2. Ask for live host RAM, CPU model/core count, storage type/capacity/free space, current LXC limits, and actual service/container metrics if they are not provided. Current operator-reported snapshot: 16 GB total host RAM; CT 100 + CT 101 have 6 GB allocated combined and use about 2 GB and change; PVE API reports about 2.70 GB current / 2.74 GB 30-day peak node use. Keep these measures distinct and verify timestamps, API fields and what node usage includes.
3. Use Prometheus/Grafana or host/container measurements under representative load to size CPU and memory. Include OS, Docker, filesystem cache and burst headroom; do not allocate the full host RAM to containers.
4. Size persistent storage and backup retention separately from RAM/CPU. Include PostgreSQL growth, Kafka retention, Elasticsearch indexes, Garage objects, Prometheus retention, logs, and backup copies.
5. Explain assumptions and give a conservative starting allocation plus the metrics/thresholds that would trigger resizing. Current operator-reported plan is CT 102 at 4 vCPU / 8 GB RAM / 80 GB disk, alongside existing CT 100 and CT 101; distinguish their combined 6 GB allocated from their reported actual use of about 2 GB and change. The older 4-vCPU/12-GB/100-GB design is superseded as a proposal, not a measured requirement.
6. For availability requirements, point out that the current single-node Compose architecture has no service-level HA. Recommend a topology change only after clarifying downtime and recovery targets.

## Useful files

- `docker-compose.yml` — service definitions, ports, volumes, environment and health checks.
- `Dockerfile` — backend image build and runtime.
- `infra/nginx.conf` — reverse proxy configuration.
- `infra/prometheus.yml` — Prometheus scrape configuration.
- `infra/garage.toml` — Garage configuration.
- `scripts/backup-db.sh` — PostgreSQL dump and restic backup workflow.
- `docs/11-cloud-free-tier.md` — older broader infrastructure proposal; its 4-vCPU/12-GB/100-GB Knowledge Gym LXC sizing is superseded by the operator's current CT 102 proposal unless live measurements justify otherwise.
