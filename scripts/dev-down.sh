#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/dev-down.sh [docker-compose arguments...]

Stop the source-built development stack. Pass --volumes after -- to remove data.
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

docker compose -f compose.yaml -f compose.dev.yaml down "$@"
