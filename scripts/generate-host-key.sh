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
# Every path this run would write, not just the private key. Guarding the key alone
# left the public half unprotected: with the key gone and `hostkey.ser.pub` still
# there, the `mv` below replaced it with the public half of a different key, so the
# fingerprint an operator had published no longer matched the server and every student
# clone stopped with a host key warning. The scratch paths ssh-keygen writes to are
# checked as well, because it answers an existing one with an interactive overwrite
# prompt that a non-interactive run cannot get past.
for path in "$target" "$target.pub" "${target%.ser}" "${target%.ser}.pub"; do
  if [[ -e "$path" ]]; then
    printf 'Refusing to overwrite existing file: %s\n' "$path" >&2
    exit 1
  fi
done

ssh-keygen -t ed25519 -N '' -f "${target%.ser}"
mv "${target%.ser}" "$target"
mv "${target%.ser}.pub" "${target}.pub"
chmod 600 "$target"
printf 'Generated SSH host key at %s\n' "$target"
