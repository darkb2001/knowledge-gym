#!/usr/bin/env bash
# Elasticsearch retention / disk guard for Knowledge Gym.
#
# 1) Delete legacy date-suffixed indices: <prefix>-YYYY.MM.DD older than
#    ES_RETENTION_DAYS (rollover naming — unused today but kept for future ILM).
# 2) Report store size for the live single index (default knowledge-gym-search).
# 3) Exit non-zero if that index exceeds ES_MAX_SIZE_GB so host cron/monitoring
#    fails loudly before the LXC thin pool fills.
#
# Document GC for searchable content is source-driven (Postgres deletes +
# search_outbox / reindex-on-startup). SearchOutboxPruner bounds the outbox table.
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX_PREFIX="${INDEX_PREFIX:-knowledge-gym}"
LIVE_INDEX="${ES_LIVE_INDEX:-knowledge-gym-search}"
RETENTION_DAYS="${ES_RETENTION_DAYS:-30}"
MAX_SIZE_GB="${ES_MAX_SIZE_GB:-20}"

case "$RETENTION_DAYS" in
  ''|*[!0-9]*) echo "ES_RETENTION_DAYS must be a positive integer" >&2; exit 2 ;;
esac
if [ "$RETENTION_DAYS" -lt 1 ]; then
  echo "ES_RETENTION_DAYS must be at least 1" >&2
  exit 2
fi
case "$MAX_SIZE_GB" in
  ''|*[!0-9]*) echo "ES_MAX_SIZE_GB must be a positive integer" >&2; exit 2 ;;
esac

cutoff="$(date -u -d "-${RETENTION_DAYS} days" +%Y.%m.%d 2>/dev/null || date -u -v-"${RETENTION_DAYS}"d +%Y.%m.%d)"
base="${ES_URL%/}"

echo "[es-retention] ES_URL=$base live=$LIVE_INDEX cutoff=$cutoff max=${MAX_SIZE_GB}GiB"

# --- dated rollover indices (no-op when none exist) ---
set +e
dated="$(curl --fail --silent --show-error "$base/_cat/indices/${INDEX_PREFIX}-*?h=index&format=txt" 2>/dev/null)"
dated_rc=$?
set -e
if [ "$dated_rc" -eq 0 ] && [ -n "${dated:-}" ]; then
  echo "$dated" | tr -d '\r' | awk -v cutoff="$cutoff" '
      $0 ~ /^[^ ]+-[0-9]{4}\.[0-9]{2}\.[0-9]{2}$/ {
        split($0, parts, "-"); date=parts[length(parts)];
        if (date < cutoff) print $0;
      }
    ' | while IFS= read -r index; do
      [ -z "$index" ] && continue
      echo "Deleting expired Elasticsearch index: $index"
      curl --fail --silent --show-error -X DELETE "$base/$index" >/dev/null
    done
else
  echo "  (no dated ${INDEX_PREFIX}-* indices — skip)"
fi

# --- live index size guard ---
set +e
line="$(curl --fail --silent --show-error \
  "$base/_cat/indices/${LIVE_INDEX}?h=index,store.size,docs.count&bytes=b" 2>/dev/null)"
live_rc=$?
set -e
if [ "$live_rc" -ne 0 ] || [ -z "${line:-}" ]; then
  echo "WARNING: live index ${LIVE_INDEX} not found or ES unreachable — skip size guard" >&2
  exit 0
fi

# _cat may return "index size docs" — size in bytes when bytes=b
index_name="$(echo "$line" | awk '{print $1}')"
store_bytes="$(echo "$line" | awk '{print $2}')"
docs="$(echo "$line" | awk '{print $3}')"
echo "  live index=$index_name docs=$docs store_bytes=$store_bytes"

max_bytes=$((MAX_SIZE_GB * 1024 * 1024 * 1024))
if [ "$store_bytes" -gt "$max_bytes" ]; then
  echo "ERROR: ${LIVE_INDEX} store size ${store_bytes} bytes exceeds ${MAX_SIZE_GB} GiB" >&2
  echo "Action: stop ingest, run reindex after pruning source data, or raise ES_MAX_SIZE_GB with ops approval." >&2
  exit 1
fi

# Expunge deletes only — safe housekeeping, does not drop searchable docs.
if [ "${ES_FORCEMERGE:-0}" = "1" ]; then
  echo "  → forcemerge only_expunge_deletes"
  curl --fail --silent --show-error -X POST \
    "$base/${LIVE_INDEX}/_forcemerge?only_expunge_deletes=true" >/dev/null
fi

echo "[es-retention] OK"
