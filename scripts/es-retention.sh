#!/usr/bin/env bash
# Delete date-suffixed Elasticsearch indices older than ES_RETENTION_DAYS.
# Run from the LXC host/container with curl and an ES endpoint reachable.
#
# NOTE: this only matches indices named `<prefix>-YYYY.MM.DD`, i.e. a rollover
# setup. The search index shipped in V023 is a single index
# (`knowledge-gym-search`, see app.search.elasticsearch.index) with no date
# suffix, so this script is a no-op for it by design — document retention there
# is handled by deleting documents, not indices, plus the search_outbox pruner.
# Wire this up only if index rollover is adopted later.
set -euo pipefail

ES_URL="${ES_URL:-http://localhost:9200}"
INDEX_PREFIX="${INDEX_PREFIX:-knowledge-gym}"
RETENTION_DAYS="${ES_RETENTION_DAYS:-30}"

case "$RETENTION_DAYS" in
  ''|*[!0-9]*) echo "ES_RETENTION_DAYS must be a positive integer" >&2; exit 2 ;;
esac
if [ "$RETENTION_DAYS" -lt 1 ]; then
  echo "ES_RETENTION_DAYS must be at least 1" >&2
  exit 2
fi

cutoff="$(date -u -d "-${RETENTION_DAYS} days" +%Y.%m.%d 2>/dev/null || date -u -v-"${RETENTION_DAYS}"d +%Y.%m.%d)"
base="${ES_URL%/}"

curl --fail --silent --show-error "$base/_cat/indices/${INDEX_PREFIX}-*?h=index&format=txt" \
  | tr -d '\r' \
  | awk -v cutoff="$cutoff" '
      $0 ~ /^[^ ]+-[0-9]{4}\.[0-9]{2}\.[0-9]{2}$/ {
        split($0, parts, "-"); date=parts[length(parts)];
        if (date < cutoff) print $0;
      }
    ' \
  | while IFS= read -r index; do
      [ -z "$index" ] && continue
      echo "Deleting expired Elasticsearch index: $index"
      curl --fail --silent --show-error -X DELETE "$base/$index" >/dev/null
    done
