#!/usr/bin/env bash
# Pull the published GHCR image and restart the prod stack — never build on the LXC.
# Honours elasticsearch.stopped marker so admin "stop ES" survives compose up.
set -euo pipefail

ROOT="${KG_ROOT:-/opt/knowledge-gym}"
COMPOSE_FILE="${KG_COMPOSE_FILE:-$ROOT/docker-compose.prod.yml}"
ES_STOP_MARKER="${KG_ES_STOP_MARKER:-$ROOT/run/elasticsearch.stopped}"

cd "$ROOT"
docker compose -f "$COMPOSE_FILE" pull app
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans

# `up -d` would otherwise start elasticsearch again even after an intentional stop
# (restart: unless-stopped does not block an explicit compose up).
if [ -f "$ES_STOP_MARKER" ]; then
  echo "ES stop marker present ($ES_STOP_MARKER) — leaving elasticsearch stopped"
  docker compose -f "$COMPOSE_FILE" stop elasticsearch
fi

docker compose -f "$COMPOSE_FILE" ps
