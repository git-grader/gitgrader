#!/usr/bin/env bash
set -euo pipefail

usage() {
  cat <<'EOF'
Usage: scripts/generate-host-key.sh [path]

Generate the persistent SSH host key used by GitGrader's embedded SSH server.
The default path is ./data/git/ssh/hostkey.ser, matching the default deployment.
EOF
}

case "${1:-}" in
  -h|--help) usage; exit 0 ;;
esac

target="${1:-./data/git/ssh/hostkey.ser}"
if [[ $# -gt 1 ]]; then
  usage >&2
  exit 2
fi

mkdir -p "$(dirname "$target")"
if [[ -e "$target" ]]; then
  printf 'Refusing to overwrite existing host key: %s\n' "$target" >&2
  exit 1
fi

ssh-keygen -t ed25519 -N '' -f "${target%.ser}"
mv "${target%.ser}" "$target"
mv "${target%.ser}.pub" "${target}.pub"
chmod 600 "$target"
printf 'Generated SSH host key at %s\n' "$target"
