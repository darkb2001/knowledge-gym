#!/usr/bin/env bash
# Non-destructive restore drill against a scratch database.
# Does NOT overwrite production Garage volumes.
set -euo pipefail

RESTIC_BUCKET="${RESTIC_BUCKET:-b2:kg-db-backups}"
RESTIC_PASSWORD_FILE="${RESTIC_PASSWORD_FILE:-/etc/kg-backup/restic.pw}"
PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5432}"
PG_USER="${PG_USER:-postgres}"
RESTORE_DB="${RESTORE_DB:-knowledgegym_restore}"
TARGET="${RESTORE_TARGET:-/tmp/kg-restore}"
SKIP_PG="${SKIP_PG_RESTORE:-0}"

mkdir -p "$TARGET"
echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] restore drill → $TARGET (db=$RESTORE_DB)"

echo "→ list recent db snapshots"
restic --repo "$RESTIC_BUCKET" snapshots --tag kg-db --last 5

echo "→ restore latest kg-db"
restic --repo "$RESTIC_BUCKET" restore latest --tag kg-db --target "$TARGET"

DUMP="$(find "$TARGET" -type f -name 'kg-*.dump' | sort | tail -1 || true)"
if [ -z "$DUMP" ]; then
  echo "ERROR: no kg-*.dump found under $TARGET" >&2
  exit 1
fi
echo "  dump: $DUMP ($(du -h "$DUMP" | cut -f1))"

if [ "$SKIP_PG" = "1" ]; then
  echo "  (SKIP_PG_RESTORE=1 — not calling pg_restore)"
else
  echo "→ pg_restore into $RESTORE_DB"
  pg_restore -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$RESTORE_DB" \
    --no-owner --clean --if-exists "$DUMP"
  psql -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -d "$RESTORE_DB" -v ON_ERROR_STOP=1 \
    -c 'SELECT COUNT(*) AS users FROM users;' \
    -c 'SELECT COUNT(*) AS questions FROM questions;'
fi

if restic --repo "$RESTIC_BUCKET" snapshots --tag kg-garage --last 1 >/dev/null 2>&1; then
  GARAGE_TARGET="${TARGET}-garage"
  mkdir -p "$GARAGE_TARGET"
  echo "→ restore latest kg-garage → $GARAGE_TARGET (inspect only)"
  restic --repo "$RESTIC_BUCKET" restore latest --tag kg-garage --target "$GARAGE_TARGET"
  find "$GARAGE_TARGET" -type f | head -20
else
  echo "  (no kg-garage snapshots — skip)"
fi

if restic --repo "$RESTIC_BUCKET" snapshots --tag kg-config --last 1 >/dev/null 2>&1; then
  CONFIG_TARGET="${TARGET}-config"
  mkdir -p "$CONFIG_TARGET"
  echo "→ restore latest kg-config → $CONFIG_TARGET"
  restic --repo "$RESTIC_BUCKET" restore latest --tag kg-config --target "$CONFIG_TARGET"
  ls -la "$CONFIG_TARGET" | head -40
else
  echo "  (no kg-config snapshots — skip)"
fi

echo "[$(date -u '+%Y-%m-%dT%H:%M:%SZ')] restore drill finished OK"
echo "Record the run in docs/20-backup-restore-drill.md"
