#!/usr/bin/env bash
# Allowlisted Elasticsearch lifecycle helper for the Proxmox/LXC host.
# Do not mount the Docker socket into the application container.
set -euo pipefail

COMPOSE_FILE="${KG_COMPOSE_FILE:-/opt/knowledge-gym/docker-compose.prod.yml}"
case "${1:-}" in
  status)
    docker compose -f "$COMPOSE_FILE" ps elasticsearch
    ;;
  start)
    docker compose -f "$COMPOSE_FILE" up -d elasticsearch
    ;;
  stop)
    docker compose -f "$COMPOSE_FILE" stop elasticsearch
    ;;
  *)
    echo "usage: $0 {status|start|stop}" >&2
    exit 2
    ;;
esac
