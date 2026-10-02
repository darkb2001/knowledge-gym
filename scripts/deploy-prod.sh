#!/usr/bin/env bash
# Pull the published GHCR image and restart the prod stack — never build on the LXC.
set -euo pipefail

ROOT="${KG_ROOT:-/opt/knowledge-gym}"
COMPOSE_FILE="${KG_COMPOSE_FILE:-$ROOT/docker-compose.prod.yml}"

cd "$ROOT"
docker compose -f "$COMPOSE_FILE" pull app
docker compose -f "$COMPOSE_FILE" up -d --remove-orphans
docker compose -f "$COMPOSE_FILE" ps
