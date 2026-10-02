# ADR-004: Infrastructure, backup, and email boundaries

- **Status:** Accepted (m11b)
- **Date:** 2026-10-02
- **Supersedes:** draft staging notes in earlier m11 reviews

## Context

The production target is a single unprivileged Proxmox LXC (CT 102) with a
constrained memory and disk budget. PostgreSQL and Garage contain durable
application data; Elasticsearch and Kafka are rebuildable. Email delivery and
offsite backup must not turn the local node into a larger, fragile platform.

## Decision

| Concern | Choice | Fallback / deferral |
|---|---|---|
| App runtime | LXC 102, Docker Compose prod, Nginx, Cloudflare Tunnel | — |
| Object storage | Garage (S3-compatible) local | Offsite copy via restic |
| DB durability | `pg_dump` → restic → Backblaze B2 | Restore drill required (`docs/20-backup-restore-drill.md`) |
| Config / secrets | restic tag `kg-config` | Same B2 repo |
| PBS | **Deferred** | Same-disk PBS is not offsite; B2 remains SoT |
| Email | **Gmail or Brevo SMTP** via `GmailEmailService` | Mailu LXC 103 deferred until DNS + RAM allow |
| Metrics offsite | Prometheus `remote_write` → Grafana Cloud Free | Local Prometheus/Grafana stay primary; series-discard alert required |
| External cron | Terraform AWS Lambda + EventBridge (`infra/terraform/aws/`) | LXC crontab until apply; `APP_CRON_MODE=external` when Lambda live |
| ES disk | Nightly `scripts/es-retention.sh` size guard + `SearchOutboxPruner` | Dated-index deletion ready if rollover is adopted |
| AWS cost | Budgets alarm at **$1**/month | — |

Firebase Hosting and Terraform Proxmox LXC resources remain out of scope.

## Consequences

- Recovery is a **restore procedure**, not a Proxmox snapshot.
- Garage backup increases B2 usage; free 10 GB may require paid tier or size filters.
- Grafana Cloud Free silently discards over-series samples — the discard alert is mandatory.
- `/internal/backup` from Lambda only works if the app (or a host agent) can run
  `backup-db.sh` with `pg_dump`/`restic`/`docker`; otherwise keep backup on host cron.
- Mailu can be revisited without redesigning auth: swap SMTP settings only.

## References

- `docs/19-m11b-ops.md`
- `docs/20-backup-restore-drill.md`
- `infra/terraform/aws/README.md`
- `scripts/backup-db.sh`, `scripts/es-retention.sh`, `scripts/restore-drill.sh`
