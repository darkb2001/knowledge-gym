#!/usr/bin/env bash
# scripts/backup-db.sh — PostgreSQL pg_dump + restic → Backblaze B2 (10 GB free vĩnh viễn)
# Schedule: chạy qua cron hoặc Lambda (mini-phase-11 Lambda cron mỗi 3h sáng)
#
# Prerequisite:
#   1. Sign up free: https://www.backblaze.com/b2 (no credit card)
#   2. Tạo Bucket: "kg-db-backups"
#   3. Tạo Application Key với quyền: read+write+list bucket "kg-db-backups"
#   4. Lấy B2_KEY_ID + B2_APPLICATION_KEY
#   5. Set env: B2_KEY_ID, B2_APP_KEY, RESTIC_PASSWORD_FILE
#
# Crontab (host):
#   0 3 * * *  /opt/knowledge-gym/scripts/backup-db.sh >> /var/log/kg-backup.log 2>&1

set -euo pipefail

# === CONFIG ===
PG_HOST="${PG_HOST:-localhost}"
PG_PORT="${PG_PORT:-5432}"
PG_DB="${PG_DB:-knowledgegym}"
PG_USER="${PG_USER:-postgres}"
BACKUP_DIR="${BACKUP_DIR:-/var/backups/kg-db}"
RESTIC_BUCKET="${RESTIC_BUCKET:-b2:kg-db-backups}"
RESTIC_PASSWORD_FILE="${RESTIC_PASSWORD_FILE:-/etc/kg-backup/restic.pw}"
KEEP_DAILY=7
KEEP_WEEKLY=4
KEEP_MONTHLY=6

mkdir -p "$BACKUP_DIR"
chmod 700 "$BACKUP_DIR"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] === Knowledge Gym DB Backup ==="

# 1. pg_dump (custom format → compress tốt)
DUMP_FILE="$BACKUP_DIR/kg-$(date +%Y%m%d-%H%M%S).sql.gz"
echo "  → pg_dump $PG_DB → $DUMP_FILE"
pg_dump -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -w -F c "$PG_DB" | gzip > "$DUMP_FILE"
DUMP_SIZE=$(du -h "$DUMP_FILE" | cut -f1)
echo "  ✓ Dump size: $DUMP_SIZE"

# 2. restic backup → B2 (encrypted, deduplicated, incremental)
echo "  → restic backup → $RESTIC_BUCKET"
restic backup \
  --tag "kg-db" \
  --tag "daily" \
  --compression max \
  "$DUMP_FILE"
echo "  ✓ Backup uploaded to B2"

# 3. Retention policy (xóa snapshot cũ, thu hồi storage)
echo "  → Pruning old snapshots (keep daily=$KEEP_DAILY weekly=$KEEP_WEEKLY monthly=$KEEP_MONTHLY)"
restic forget \
  --keep-daily "$KEEP_DAILY" \
  --keep-weekly "$KEEP_WEEKLY" \
  --keep-monthly "$KEEP_MONTHLY" \
  --prune
echo "  ✓ Retention applied"

# 4. Verify
echo "  → Snapshots:"
restic snapshots --tag "kg-db" --last 5

# 5. Dọn local dump (giữ 3 file mới nhất)
ls -1t "$BACKUP_DIR"/kg-*.sql.gz | tail -n +4 | xargs -r rm --
echo "  ✓ Local cleanup (keep last 3)"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] === DONE ==="
echo ""

# Restore command (giữ làm comment cho reference):
#   restic -r b2:kg-db-backups snapshots --tag kg-db
#   restic -r b2:kg-db-backups restore latest --target /tmp/restore
#   pg_restore -h localhost -U postgres -d knowledgegym_new /tmp/restore/var/backups/kg-db/kg-*.sql.gz