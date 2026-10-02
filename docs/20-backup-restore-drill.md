# Backup restore drill (m11b acceptance)

An untested restic snapshot is not a backup. Run this on a scratch DB / empty volume
**before** admitting real user uploads to Garage.

## Prerequisites

- Latest successful `scripts/backup-db.sh` run with `GARAGE_BACKUP=1` and `CONFIG_BACKUP=1`
- `restic` + `pg_restore` + Docker on the restore host
- Scratch Postgres database `knowledgegym_restore` (empty)
- Enough disk under `/tmp/kg-restore`

## Drill steps

```bash
export RESTIC_BUCKET="${RESTIC_BUCKET:-b2:kg-db-backups}"
export RESTIC_PASSWORD_FILE="${RESTIC_PASSWORD_FILE:-/etc/kg-backup/restic.pw}"
# B2_ACCOUNT_ID / B2_ACCOUNT_KEY or restic B2 env as used in backup

mkdir -p /tmp/kg-restore
restic --repo "$RESTIC_BUCKET" snapshots --tag kg-db --last 3
restic --repo "$RESTIC_BUCKET" restore latest --tag kg-db --target /tmp/kg-restore

DUMP=$(find /tmp/kg-restore -name 'kg-*.dump' | sort | tail -1)
test -n "$DUMP"
pg_restore -h "$PG_HOST" -U "$PG_USER" -d knowledgegym_restore \
  --no-owner --clean --if-exists "$DUMP"

# Spot-checks (adjust queries to your schema)
psql -h "$PG_HOST" -U "$PG_USER" -d knowledgegym_restore -c 'SELECT COUNT(*) FROM users;'
psql -h "$PG_HOST" -U "$PG_USER" -d knowledgegym_restore -c 'SELECT COUNT(*) FROM questions;'

# Garage objects (optional if GARAGE_BACKUP=1 was on)
restic --repo "$RESTIC_BUCKET" restore latest --tag kg-garage --target /tmp/kg-restore-garage
# Inspect files; do NOT overwrite prod volume during the first drill.

# Config
restic --repo "$RESTIC_BUCKET" restore latest --tag kg-config --target /tmp/kg-restore-config
test -f /tmp/kg-restore-config/.env.example || test -f /tmp/kg-restore-config/docker-compose.prod.yml
```

Helper (same steps, non-interactive checks): `scripts/restore-drill.sh`.

## Pass criteria

- [ ] `pg_restore` exits 0 (warnings about roles OK; errors about missing relations are not)
- [ ] `users` and `questions` counts ≥ 0 and match expectations vs prod spot-check
- [ ] Garage restore directory non-empty when Garage backup was enabled
- [ ] Config restore contains compose and env example (and `.env` if it was backed up)

## Drill log

| Date (UTC) | Operator | Snapshot ID | Result | Notes |
|---|---|---|---|---|
| _pending_ | | | | Fill after first successful drill on CT102 / scratch host |
