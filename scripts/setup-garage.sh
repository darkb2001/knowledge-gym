#!/usr/bin/env bash
# scripts/setup-garage.sh — Tạo buckets + access key trong Garage
# Chạy 1 lần sau khi garage container start: bash scripts/setup-garage.sh
set -euo pipefail

GARAGE_ADMIN="http://localhost:3901"
GARAGE_ADMIN_TOKEN="${GARAGE_ADMIN_TOKEN:-admin}"

# 1. Kiểm tra Garage đang chạy
if ! curl -sf "$GARAGE_ADMIN/v1/health" >/dev/null; then
  echo "✗ Garage not running at $GARAGE_ADMIN"
  exit 1
fi

echo "✓ Garage is healthy"

# 2. Lấy node ID (single-node bootstrap)
NODE_ID=$(curl -s -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" \
  "$GARAGE_ADMIN/v1/status" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)

echo "  Node ID: $NODE_ID"

# 3. Assign layout (single-node: zone 1, capacity 100GB)
curl -s -X PUT -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" \
  -H "Content-Type: application/json" \
  "$GARAGE_ADMIN/v1/layout" \
  -d "{\"version\": 1, \"roles\": [{\"id\": \"$NODE_ID\", \"zone\": \"dc1\", \"capacity\": \"100G\", \"tags\": []}]}" | jq . 2>/dev/null || echo "(layout assigned)"

echo "✓ Layout assigned"

# 4. Tạo buckets
for BUCKET in kg-blog-images kg-avatars kg-exports kg-backups; do
  curl -s -X POST -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" \
    "$GARAGE_ADMIN/v1/bucket" \
    -d "{\"id\": \"$BUCKET\", \"globalAliases\": [\"$BUCKET\"]}" | jq . 2>/dev/null || echo "  bucket $BUCKET created"
done

echo "✓ Buckets created"

# 5. Tạo access key
KEY_RESP=$(curl -s -X POST -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" \
  "$GARAGE_ADMIN/v1/key" \
  -d '{"name": "kg-app"}')

ACCESS_KEY=$(echo "$KEY_RESP" | grep -o '"accessKeyId":"[^"]*"' | cut -d'"' -f4)
SECRET_KEY=$(echo "$KEY_RESP" | grep -o '"secretAccessKey":"[^"]*"' | cut -d'"' -f4)

echo ""
echo "============================================"
echo "  Garage Access Key (copy to .env)"
echo "============================================"
echo "  GARAGE_ACCESS_KEY=$ACCESS_KEY"
echo "  GARAGE_SECRET_KEY=$SECRET_KEY"
echo "============================================"
echo ""

# 6. Gán key vào buckets
for BUCKET in kg-blog-images kg-avatars kg-exports kg-backups; do
  curl -s -X POST -H "Authorization: Bearer $GARAGE_ADMIN_TOKEN" \
    "$GARAGE_ADMIN/v1/bucket/$BUCKET/key/$ACCESS_KEY" \
    -d '{"permissions": {"read": true, "write": true, "owner": true}}' >/dev/null
done

echo "✓ Key permissions granted to all buckets"
echo ""
echo "Add to .env:"
echo "  GARAGE_ACCESS_KEY=$ACCESS_KEY"
echo "  GARAGE_SECRET_KEY=$SECRET_KEY"
echo "  GARAGE_ADMIN_TOKEN=$GARAGE_ADMIN_TOKEN"