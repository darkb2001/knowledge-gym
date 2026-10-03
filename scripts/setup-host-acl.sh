#!/usr/bin/env bash
# Bootstrap the minimum host ACLs for the kg-es-control systemd user.
# Run as root on CT102 after /opt/kg/.env and /opt/kg/run exist.
set -euo pipefail

ROOT="${KG_ROOT:-/opt/kg}"
CONTROL_USER="${KG_CONTROL_USER:-kgctl}"

if [ "$(id -u)" -ne 0 ]; then
  echo "run as root" >&2
  exit 2
fi
if ! id "$CONTROL_USER" >/dev/null 2>&1; then
  echo "missing control user: $CONTROL_USER" >&2
  exit 2
fi
if ! command -v setfacl >/dev/null 2>&1; then
  echo "setfacl is required (install the acl package)" >&2
  exit 2
fi

install -d -m 0750 "$ROOT/run"
test -f "$ROOT/.env" || {
  echo "missing $ROOT/.env" >&2
  exit 2
}

# The agent reads only the environment and writes only the lifecycle marker.
setfacl -m "u:${CONTROL_USER}:r" "$ROOT/.env"
setfacl -m "u:${CONTROL_USER}:rwx" "$ROOT/run"

echo "ACLs installed for ${CONTROL_USER}:"
getfacl -cp "$ROOT/.env" "$ROOT/run"
