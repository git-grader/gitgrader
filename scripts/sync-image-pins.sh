#!/usr/bin/env bash
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# Every path below is relative to the repository, not to wherever this was called from,
# so the script has to stand where it expects to.
cd "$(dirname "$0")/.."

usage() {
  cat <<'EOF'
Usage: scripts/sync-image-pins.sh [--check|--fix]

Two files carry a copy of an image pin that Dependabot updates somewhere else and
cannot see: examples/seed-data.sql seeds the runtime that
deployment/runtimes/node-24/Dockerfile builds, and scripts/lib.sh names the helper
image compose.yaml already pins.

  --check  report drift and exit 1 (default; this is what the Quality workflow runs)
  --fix    rewrite the trailing copies to match, then report what moved
EOF
}

mode=check
case "${1:-}" in
  '' | --check) ;;
  --fix) mode=fix ;;
  -h | --help)
    usage
    exit 0
    ;;
  *)
    printf 'error: unknown argument %s\n\n' "$1" >&2
    usage >&2
    exit 2
    ;;
esac

drift=0

# Located and extracted in two steps because the locator has to be narrow enough to pick
# the right line and the value narrow enough to be swapped: compose.yaml also carries a
# postgres:18.6-alpine image, and matching on "alpine" alone would eventually find it.
# grep -m1 rather than a pipe into head, which would leave the first grep on a closed
# pipe and fail the whole pipeline under pipefail once a file grows a second match.
read_pin() {
  local file=$1 locator=$2 value=$3
  local found
  if ! found=$(grep -m1 -oE "$locator" "$file" | grep -oE "$value"); then
    printf 'error: %s no longer holds a pin matching /%s/\n' "$file" "$locator" >&2
    return 1
  fi
  printf '%s\n' "$found"
}

sync_pin() {
  local label=$1 source_file=$2 source_locator=$3 mirror_file=$4 mirror_locator=$5 value=$6
  local want have tmp
  want=$(read_pin "$source_file" "$source_locator" "$value")
  have=$(read_pin "$mirror_file" "$mirror_locator" "$value")

  if [[ $want == "$have" ]]; then
    printf '%s is in step at %s\n' "$label" "$want"
    return 0
  fi

  if [[ $mode == check ]]; then
    printf '::error::%s pins %s but %s pins %s. Run scripts/sync-image-pins.sh --fix.\n' \
      "$source_file" "$want" "$mirror_file" "$have" >&2
    drift=1
    return 0
  fi

  # A literal swap of the first occurrence, so a rewrite can only ever touch the pin the
  # check compares and never a digest that appears further down the file.
  tmp=$(mktemp)
  awk -v old="$have" -v new="$want" '
    !swapped {
      at = index($0, old)
      if (at) {
        $0 = substr($0, 1, at - 1) new substr($0, at + length(old))
        swapped = 1
      }
    }
    { print }
  ' "$mirror_file" >"$tmp"
  # Written back through the existing file rather than moved over it, so the mode the
  # repository tracks survives.
  cat "$tmp" >"$mirror_file"
  rm -f "$tmp"
  printf '%s moved from %s to %s in %s\n' "$label" "$have" "$want" "$mirror_file"
}

sync_pin 'The node-24 runtime digest' \
  deployment/runtimes/node-24/Dockerfile 'sha256:[a-f0-9]+' \
  examples/seed-data.sql 'sha256:[a-f0-9]+' \
  'sha256:[a-f0-9]+'

sync_pin 'The alpine helper image' \
  compose.yaml 'image: alpine:[0-9.]+' \
  scripts/lib.sh 'ALPINE_IMAGE="alpine:[0-9.]+"' \
  'alpine:[0-9.]+'

exit "$drift"
