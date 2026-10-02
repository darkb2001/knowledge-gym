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

# 1. pg_dump (custom format → nén sẵn, pg_restore đọc trực tiếp)
#
# KHÔNG pipe qua gzip: `-F c` đã là archive nén, bọc thêm gzip sẽ tạo file mà
# `pg_restore` không đọc được (phải gunzip -c trước) — bản cũ làm đúng như vậy
# nên file backup không restore thẳng được.
DUMP_FILE="$BACKUP_DIR/kg-$(date +%Y%m%d-%H%M%S).dump"
echo "  → pg_dump $PG_DB → $DUMP_FILE"
# `-w` = không hỏi password; cấp qua PGPASSWORD hoặc ~/.pgpass.
pg_dump -h "$PG_HOST" -p "$PG_PORT" -U "$PG_USER" -w -F c -f "$DUMP_FILE" "$PG_DB"
DUMP_SIZE=$(du -h "$DUMP_FILE" | cut -f1)
echo "  ✓ Dump size: $DUMP_SIZE"

# 2. restic backup → B2 (encrypted, deduplicated, incremental)
#    `--repo` tường minh: RESTIC_BUCKET ở trên chỉ là biến của script, restic
#    KHÔNG tự đọc nó — thiếu flag này thì restic đòi RESTIC_REPOSITORY và fail
#    với "Please specify repository location".
echo "  → restic backup → $RESTIC_BUCKET"
restic --repo "$RESTIC_BUCKET" backup \
  --tag "kg-db" \
  --tag "daily" \
  --compression max \
  "$DUMP_FILE"
echo "  ✓ Backup uploaded to B2"

# 3. Retention policy (xóa snapshot cũ, thu hồi storage)
echo "  → Pruning old snapshots (keep daily=$KEEP_DAILY weekly=$KEEP_WEEKLY monthly=$KEEP_MONTHLY)"
restic --repo "$RESTIC_BUCKET" forget \
  --keep-daily "$KEEP_DAILY" \
  --keep-weekly "$KEEP_WEEKLY" \
  --keep-monthly "$KEEP_MONTHLY" \
  --prune
echo "  ✓ Retention applied"

# 4. Verify
echo "  → Snapshots:"
restic --repo "$RESTIC_BUCKET" snapshots --tag "kg-db" --last 5

# 5. Garage (object storage) — ảnh/avatar user upload KHÔNG tái tạo được từ
#    Postgres. Elasticsearch/Kafka/Prometheus dựng lại được, Garage thì không.
#    Bật bằng GARAGE_BACKUP=1 (mặc định tắt để không phá setup cũ).
#    Cách làm: copy volume garage-data ra staging read-only rồi restic đọc —
#    an toàn với Garage đang chạy (chỉ đọc file, không lock).
if [ "${GARAGE_BACKUP:-0}" = "1" ]; then
  GARAGE_VOLUME="${GARAGE_VOLUME:-knowledge-gym_garage-data}"
  GARAGE_STAGE="${GARAGE_STAGE:-/var/backups/kg-garage}"
  mkdir -p "$GARAGE_STAGE"
  echo "  → stage garage volume ($GARAGE_VOLUME) → $GARAGE_STAGE"
  docker run --rm -v "$GARAGE_VOLUME":/data:ro -v "$GARAGE_STAGE":/staging \
    alpine:3.20 sh -c 'cp -a /data/. /staging/'
  restic --repo "$RESTIC_BUCKET" backup --tag "kg-garage" --tag "daily" \
    --compression max "$GARAGE_STAGE"
  echo "  ✓ Garage backup uploaded"
  restic --repo "$RESTIC_BUCKET" forget --tag "kg-garage" \
    --keep-daily "$KEEP_DAILY" --keep-weekly "$KEEP_WEEKLY" --keep-monthly "$KEEP_MONTHLY" --prune
  echo "  ✓ Garage retention applied"
else
  echo "  (Garage backup bỏ qua — set GARAGE_BACKUP=1 để bật)"
fi

# 6. Config snapshot — .env + compose + infra cần để rebuild CT102.
CONFIG_STAGE="${CONFIG_STAGE:-/var/backups/kg-config}"
if [ "${CONFIG_BACKUP:-1}" = "1" ]; then
  mkdir -p "$CONFIG_STAGE"
  ROOT="${KG_ROOT:-/opt/knowledge-gym}"
  echo "  → stage config from $ROOT"
  for item in docker-compose.prod.yml .env.example infra scripts; do
    if [ -e "$ROOT/$item" ]; then
      cp -a "$ROOT/$item" "$CONFIG_STAGE/"
    fi
  done
  if [ -f "$ROOT/.env" ]; then
    cp -a "$ROOT/.env" "$CONFIG_STAGE/.env"
  fi
  restic --repo "$RESTIC_BUCKET" backup --tag "kg-config" --tag "daily" \
    --compression max "$CONFIG_STAGE"
  echo "  ✓ Config backup uploaded"
  restic --repo "$RESTIC_BUCKET" forget --tag "kg-config" \
    --keep-daily "$KEEP_DAILY" --keep-weekly "$KEEP_WEEKLY" --keep-monthly "$KEEP_MONTHLY" --prune
else
  echo "  (Config backup bỏ qua — set CONFIG_BACKUP=1 để bật)"
fi

# 7. Dọn local dump (giữ 3 file mới nhất)
ls -1t "$BACKUP_DIR"/kg-*.dump | tail -n +4 | xargs -r rm --
echo "  ✓ Local cleanup (keep last 3)"

echo "[$(date '+%Y-%m-%d %H:%M:%S')] === DONE ==="
echo ""

# Restore command (giữ làm comment cho reference):
#   restic --repo b2:kg-db-backups snapshots --tag kg-db
#   restic --repo b2:kg-db-backups restore latest --target /tmp/restore
#   pg_restore -h localhost -U postgres -d knowledgegym_restore \
#     --no-owner --clean --if-exists /tmp/restore/var/backups/kg-db/kg-*.dump
#
# Garage restore: trích snapshot tag kg-garage rồi copy ngược vào volume
#   docker run --rm -v knowledge-gym_garage-data:/data \
#     -v /var/restore/kg-garage:/staging alpine:3.20 sh -c 'cp -a /staging/. /data/'