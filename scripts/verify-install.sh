#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/verify-install.sh [base-url] [ssh-host] [ssh-port]

Check the readiness endpoint, the public API meta endpoint, and TCP reachability
of the Git SSH endpoint. Defaults: http://localhost:8080, localhost, 2222.
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

base_url="${1:-http://localhost:8080}"
ssh_host="${2:-localhost}"
ssh_port="${3:-2222}"

curl --fail --silent --show-error "${base_url%/}/actuator/health/readiness" >/dev/null
curl --fail --silent --show-error "${base_url%/}/api/v1/meta" >/dev/null

if command -v nc >/dev/null 2>&1; then
  nc -z -w 5 "$ssh_host" "$ssh_port"
else
  timeout 5 bash -c "</dev/tcp/${ssh_host}/${ssh_port}"
fi

printf 'Installation checks passed: %s and %s:%s\n' "$base_url" "$ssh_host" "$ssh_port"
