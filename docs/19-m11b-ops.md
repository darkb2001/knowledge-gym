# m11b ops — email, backup, retention, metrics, Lambda

> Operator runbook for the Should-ship side of mini-phase 11. Code/IaC lives in
> the repo; live apply of B2 / Grafana Cloud / AWS still needs credentials on CT102.

## 1. Email — accepted interim (Mailu deferred)

**Decision:** production OTP / password-reset mail stays on **Gmail SMTP App Password
or Brevo SMTP** via the existing `GmailEmailService` (`app.email.smtp-enabled=true`).

Mailu (LXC 103) is **deferred** until:

- DNS MX/SPF/DKIM/DMARC is ready on a non-residential path, and
- CT RAM budget can absorb ~1–1.5 GiB without starving ES/app.

Why accepted: Mailu on the same residential uplink usually fails deliverability
tests; Brevo/Gmail already work for low-volume OTP. See ADR-004.

Prod checklist:

```bash
# .env
app.email.smtp-enabled → SPRING_MAIL_* / APP mapped vars already in compose
APP_EMAIL_SMTP_ENABLED=true   # if exposed; else spring.mail.* + app.email.smtp-enabled
```

## 2. PBS — explicitly deferred

Proxmox Backup Server on LXC 104 is **not** scheduled. Rationale: same physical
NVMe as CT 102 → not offsite. Offsite source of truth remains **restic → B2**
(Postgres + Garage + config). Revisit PBS only after a second disk or remote PBS.

## 3. Backup offsite + restore drill

Script: `scripts/backup-db.sh` (Postgres dump + optional Garage + config).

Host env (outside `.env` secrets files):

| Var | Purpose |
|---|---|
| `B2_KEY_ID` / `B2_APPLICATION_KEY` | restic B2 backend |
| `RESTIC_PASSWORD_FILE` | encryption key file |
| `GARAGE_BACKUP=1` | include Garage volume |
| `CONFIG_BACKUP=1` | include compose / `.env` / infra / scripts |
| `RESTIC_BUCKET` | default `b2:kg-db-backups` |

Cron (host, preferred until Lambda is live):

```cron
0 3 * * * GARAGE_BACKUP=1 CONFIG_BACKUP=1 /opt/kg/scripts/backup-db.sh >> /var/log/kg-backup.log 2>&1
```

Or `POST /api/v1/internal/backup` with `X-Cron-Token` when `KG_BACKUP_SCRIPT` is set
and the process can reach host tools (see `docs/17-es-admin-lifecycle-handoff.md`).

**Restore drill is mandatory** before calling backup “done”. Follow
`docs/20-backup-restore-drill.md` and record the date + operator in that file’s
log table after a successful dry-run restore.

## 4. Elasticsearch retention

Production Elasticsearch uses `-Xms768m -Xmx768m` with a `2048m` container
limit. This leaves roughly 1.25 GiB for native memory and filesystem cache while
avoiding the observed reindex pressure at a 512m heap. Recheck RSS after
large reindex operations; do not increase the heap without measuring the CT.

- Outbox: `SearchOutboxPruner` (`app.search.outbox.retention-days`, default 7).
- Index: single `knowledge-gym-search` (no dated rollover). Nightly host job:

```cron
15 4 * * * ES_URL=http://elasticsearch:9200 /opt/kg/scripts/es-retention.sh >> /var/log/kg-es-retention.log 2>&1
```

The script: deletes legacy `<prefix>-YYYY.MM.DD` indices if any; reports store
size for `knowledge-gym-search`; fails if size exceeds `ES_MAX_SIZE_GB` (default 20)
so disk exhaustion surfaces as a cron failure before the thin pool fills.

## 5. Grafana Cloud remote_write

1. Create a Grafana Cloud stack → Prometheus remote_write credentials.
2. Copy `infra/prometheus/remote_write.yml.example` → host-only
   `infra/prometheus/remote_write.secrets.yml` (gitignored pattern: `*.secrets.yml`).
3. Merge into Prometheus per comments in `infra/prometheus.yml`.
4. Keep scrape at 15s locally; remote_write sample interval / queue as in the example
   (~60s effective DPM).
5. Alert `KnowledgeGymPrometheusRemoteSamplesDiscarded` is already in
   `infra/prometheus/alerts.yml`.

## 6. AWS Lambda + EventBridge

IaC: `infra/terraform/aws/`. After `terraform apply`:

- Collector: `rate(6 hours)` → `POST /internal/collect`
- Backup: `cron(0 3 * * ?)` → `POST /internal/backup`
- Budgets alarm: $1 USD

Then set prod `APP_CRON_MODE=external` (already the `.env.example` default) so
embedded collect/writer ticks stay off while outbox relay + stale reclaim continue.

Daily-challenge Lambda is intentionally omitted until m12 exposes the endpoint.

## 7. De-scoped here

- Firebase Hosting
- Terraform Proxmox LXC resources
- Mailu / PBS (see §§1–2)
