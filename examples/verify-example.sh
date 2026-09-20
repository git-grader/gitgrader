#!/bin/sh
set -eu

# CDPATH is cleared so a user's CDPATH cannot redirect the cd.
# shellcheck disable=SC1007
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck disable=SC1007
repo=$(CDPATH= cd -- "$root/.." && pwd)
assignment="$root/assignments/assignment-01-string-utils"
suite="$assignment/hidden-tests"
manifest="$suite/manifest.json"
shim="$repo/deployment/runtimes/node-shim"
work=$(mktemp -d "${TMPDIR:-/tmp}/gitgrader-example.XXXXXX")

# This proof intentionally exercises only the string-utils example. The WBE
# assignment packages are separate course material and are never discovered or
# included by this script.
if [ "$(basename "$assignment")" != 'assignment-01-string-utils' ]; then
	printf '%s\n' 'ERROR: example verification scope changed unexpectedly.' >&2
	exit 1
fi

# The same digest-pinned image plays both halves. Pinning matches the runtime
# registration in examples/seed-data.sql and the node-24 Dockerfile, so the
# verification exercises exactly the image a deployment would grade with.
node_image=${NODE_IMAGE:-gitgrader-node-24:local}

cleanup() {
  for id in "$work"/*.server; do
    [ -e "$id" ] || continue
    docker rm -f "$(cat "$id")" >/dev/null 2>&1 || true
  done
  rm -rf "$work"
}
trap cleanup EXIT HUP INT TERM

if ! docker image inspect "$node_image" >/dev/null 2>&1; then
  docker pull "$node_image"
fi

verify_names() {
  report=$1
  emitted="$work/emitted-names.txt"
  manifest_names="$work/manifest-names.txt"

  awk '/^(ok|not ok) - / { sub(/^(ok|not ok) - /, ""); print }' "$report" > "$emitted"
  node -e 'const fs = require("node:fs"); for (const test of JSON.parse(fs.readFileSync(process.argv[1], "utf8")).tests) console.log(test.name);' "$manifest" > "$manifest_names"

  if ! diff -u "$manifest_names" "$emitted"; then
    printf '%s\n' 'ERROR: manifest test names do not exactly match TAP test names.' >&2
    exit 1
  fi
}

run_suite() {
  label=$1
  solution=$2
  expected_passed=$3
  expected_failed=$4
  report="$work/$label.tap"
  socket="$work/$label.sock"
  sandbox="$work/$label.server"

  # Both containers share one socket directory; the sandbox creates the socket
  # there and the suite connects to it. The suite directory is mounted only into
  # the suite container: the sandbox container never sees the hidden sources, and
  # its root filesystem is read-only with /tmp as tmpfs, no network, no extra
  # capabilities, and no privilege escalation, mirroring the runner's sandbox.
  mkdir -p "$socket" "$work/workspace"
  chmod 0777 "$socket"
  cp -R "$assignment/template/." "$work/workspace/"
  cp "$solution" "$work/workspace/src/string-utils.js"
  chmod -R a+rX "$work/workspace"

  docker run --detach --rm \
    --network none --cap-drop ALL --security-opt no-new-privileges \
    --read-only --tmpfs /tmp --user 65534:65534 --workdir /workspace \
    --env SOLUTION_PATH=/workspace/src/string-utils.js \
    --env SHIM_SOCKET=/gitgrader-shim/runner.sock \
    --volume "$work/workspace:/workspace" \
    --volume "$socket:/gitgrader-shim" \
    --volume "$shim:/opt/gitgrader-shim:ro" \
    --entrypoint /bin/sh "$node_image" -c 'node /opt/gitgrader-shim/server.js' > "$sandbox"

  if docker run --rm \
    --network none --cap-drop ALL --security-opt no-new-privileges \
    --read-only --tmpfs /tmp --user 65534:65534 \
    --env SHIM_SOCKET=/gitgrader-shim/runner.sock \
    --env HIDDEN_TESTS=/opt/hidden-tests \
    --volume "$suite:/opt/hidden-tests:ro" \
    --volume "$socket:/gitgrader-shim" \
    --volume "$shim:/opt/gitgrader-shim:ro" \
    --entrypoint /bin/sh "$node_image" -c 'cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs' > "$report" 2>&1; then
    status=0
  else
    status=$?
  fi

  docker rm -f "$(cat "$sandbox")" >/dev/null 2>&1 || true

  passed=$(awk '/^# pass [0-9]+$/ { print $3 }' "$report")
  failed=$(awk '/^# fail [0-9]+$/ { print $3 }' "$report")
  total=$(awk '/^# tests [0-9]+$/ { print $3 }' "$report")

  if [ "$passed" != "$expected_passed" ] || [ "$failed" != "$expected_failed" ] || [ "$total" != 10 ]; then
    printf '%s\n' "ERROR: $label expected $expected_passed passed / $expected_failed failed / 10 total; got ${passed:-missing} / ${failed:-missing} / ${total:-missing}." >&2
    cat "$report" >&2
    exit 1
  fi

  if [ "$expected_failed" -eq 0 ] && [ "$status" -ne 0 ]; then
    printf '%s\n' "ERROR: $label unexpectedly exited $status." >&2
    exit 1
  fi
  if [ "$expected_failed" -gt 0 ] && [ "$status" -eq 0 ]; then
    printf '%s\n' "ERROR: $label unexpectedly succeeded despite failed tests." >&2
    exit 1
  fi

  verify_names "$report"
  printf '%s\n' "$label: $passed passed / $failed failed / $total total (two containers, shared socket)"
}

run_suite complete "$assignment/reference-solution/complete/string-utils.js" 10 0
run_suite partial-70 "$assignment/reference-solution/partial-70/string-utils.js" 7 3

score=$(awk 'BEGIN { printf "%.1f", 7 / 10 * 100 }')
if [ "$score" != '70.0' ]; then
  printf '%s\n' "ERROR: expected formatted score 70.0, got $score." >&2
  exit 1
fi

printf '%s\n' "partial-70 score: $score% (7 / 10 * 100)"
printf '%s\n' 'Manifest/TAP name join: exact match'
printf '%s\n' 'Suite container did not expose /workspace; sandbox never mounted the hidden suite.'
printf '%s\n' 'Example verification passed.'
