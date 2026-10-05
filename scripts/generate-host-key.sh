#!/usr/bin/env bash
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

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
# ssh-keygen is handed a scratch name and the result is moved into place, so that the
# key exists at exactly the requested path whatever it is called. It cannot be given
# "$target" directly: it answers an existing file with an interactive overwrite prompt
# that a non-interactive run cannot get past.
#
# The scratch name is derived from the target rather than from "${target%.ser}", which
# only differs from the target when the target ends in .ser. For any other name - and
# the usage text presents the path as free-form - that made the guard check one path
# twice and left `mv "$scratch" "$target"` as `mv X X`, so the key was generated and
# the script then died on "are the same file" without reporting success.
scratch="${target}.new"

# Every path this run would write, not just the private key. Guarding the key alone
# left the public half unprotected: with the key gone and `hostkey.ser.pub` still
# there, the `mv` below replaced it with the public half of a different key, so the
# fingerprint an operator had published no longer matched the server and every student
# clone stopped with a host key warning. The scratch paths are checked as well, for
# the reason given above.
for path in "$target" "$target.pub" "$scratch" "$scratch.pub"; do
  if [[ -e "$path" ]]; then
    printf 'Refusing to overwrite existing file: %s\n' "$path" >&2
    exit 1
  fi
done

ssh-keygen -t ed25519 -N '' -f "$scratch"
mv "$scratch" "$target"
mv "$scratch.pub" "$target.pub"
chmod 600 "$target"
printf 'Generated SSH host key at %s\n' "$target"
