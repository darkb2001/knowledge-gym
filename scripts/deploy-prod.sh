#!/usr/bin/env bash
# Pull the published GHCR image and restart the prod stack — never build on the LXC.
# Honours elasticsearch.stopped marker so admin "stop ES" survives compose up.
set -euo pipefail

ROOT="${KG_ROOT:-/opt/kg}"
COMPOSE_FILE="${KG_COMPOSE_FILE:-$ROOT/docker-compose.prod.yml}"
ES_STOP_MARKER="${KG_ES_STOP_MARKER:-$ROOT/run/elasticsearch.stopped}"
OBS_MARKER="${KG_OBSERVABILITY_MARKER:-$ROOT/run/observability.enabled}"
COMPOSE=(docker compose -f "$COMPOSE_FILE")
if [ -f "$OBS_MARKER" ]; then
  test -f "$ROOT/docker-compose.observability.yml"
  docker network inspect kgops_telemetry >/dev/null
  COMPOSE+=(-f "$ROOT/docker-compose.observability.yml")
fi

cd "$ROOT"
APP_IMAGE="${APP_IMAGE:?APP_IMAGE must be pinned to a release tag}"
export APP_IMAGE

"${COMPOSE[@]}" pull app
"${COMPOSE[@]}" up -d --remove-orphans

# `up -d` would otherwise start elasticsearch again even after an intentional stop
# (restart: unless-stopped does not block an explicit compose up).
if [ -f "$ES_STOP_MARKER" ]; then
  echo "ES stop marker present ($ES_STOP_MARKER) — leaving elasticsearch stopped"
  "${COMPOSE[@]}" stop elasticsearch
fi

"${COMPOSE[@]}" ps
