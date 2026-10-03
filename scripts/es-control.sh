#!/usr/bin/env bash
# Allowlisted Elasticsearch lifecycle helper for CT102 (LXC with Docker).
# Prefer the kg-es-control HTTP agent (bridge gateway) from the app container —
# this script is for host/agent use. Do not mount the Docker socket into the app.
set -euo pipefail

COMPOSE_FILE="${KG_COMPOSE_FILE:-/opt/kg/docker-compose.prod.yml}"
ROOT="$(dirname "$COMPOSE_FILE")"
ES_STOP_MARKER="${KG_ES_STOP_MARKER:-$ROOT/run/elasticsearch.stopped}"

mkdir -p "$(dirname "$ES_STOP_MARKER")"

case "${1:-}" in
  status)
    docker compose -f "$COMPOSE_FILE" ps elasticsearch
    ;;
  start)
    rm -f "$ES_STOP_MARKER"
    docker compose -f "$COMPOSE_FILE" up -d elasticsearch
    ;;
  stop)
    docker compose -f "$COMPOSE_FILE" stop elasticsearch
    touch "$ES_STOP_MARKER"
    ;;
  *)
    echo "usage: $0 {status|start|stop}" >&2
    exit 2
    ;;
esac
