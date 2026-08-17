#!/usr/bin/env bash
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# The compose files are named relatively, so this has to run from the repository root
# the way its counterpart does.
cd "$(dirname "$0")/.."

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
