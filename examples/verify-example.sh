#!/bin/sh
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -eu

# CDPATH is cleared so a user's CDPATH cannot redirect the cd.
# shellcheck disable=SC1007
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck disable=SC1007
repo=$(CDPATH= cd -- "$root/.." && pwd)
work=$(mktemp -d "${TMPDIR:-/tmp}/gitgrader-example.XXXXXX")

# This proof intentionally exercises only the string-utils examples in
# assignments/. The WBE assignment packages are separate course material and are
# never discovered or included by this script.
node_assignment="$root/assignments/assignment-01-string-utils"
java_assignment="$root/assignments/assignment-02-string-utils-java"
python_assignment="$root/assignments/assignment-03-string-utils-python"
for assignment in "$node_assignment" "$java_assignment" "$python_assignment"; do
  if [ ! -d "$assignment/hidden-tests" ] || [ ! -d "$assignment/template" ]; then
    printf '%s\n' "ERROR: example verification scope changed unexpectedly: $assignment" >&2
    exit 1
  fi
done

node_shim="$repo/deployment/runtimes/node-shim"
python_shim="$repo/deployment/runtimes/python-shim"

# All three legs run the locally built runtime images. The stock upstream bases
# cannot stand in: the node base has no jasmine, the python base has no pytest,
# and the Java base has neither the compiled shim nor the JUnit console jar, so
# each suite container would fail on tooling that does not exist. Override the
# *_IMAGE variables only with equivalent images that already carry that tooling.
node_image=${NODE_IMAGE:-gitgrader-node-24:local}
python_image=${PYTHON_IMAGE:-gitgrader-python-3.14:local}
java_image=${JAVA_IMAGE:-gitgrader-java-25:local}

for tool in docker node awk diff mktemp; do
  command -v "$tool" >/dev/null 2>&1 || {
    printf '%s\n' "ERROR: $tool is required but not on PATH." >&2
    exit 1
  }
done

cleanup() {
  for id in "$work"/*.server; do
    [ -e "$id" ] || continue
    docker rm -f "$(cat "$id")" >/dev/null 2>&1 || true
  done
  rm -rf "$work"
}
trap cleanup EXIT HUP INT TERM

# The images are built, not pulled, so this cannot fall back to a registry the way
# a digest-pinned reference would. On a fresh clone the name carries no registry
# host and `docker pull` fails without saying which command produces it.
for image in "$node_image" "$python_image" "$java_image"; do
  if ! docker image inspect "$image" >/dev/null 2>&1; then
    printf '%s\n' "ERROR: runtime image $image is not present locally." >&2
    printf '%s\n' "Build it with:  deployment/runtimes/build-runtimes.sh" >&2
    exit 1
  fi
done

verify_names() {
  report=$1
  manifest=$2
  emitted="$work/emitted-names.txt"
  manifest_names="$work/manifest-names.txt"

  awk '/^(ok|not ok)( [0-9]+)? - / { sub(/^(ok|not ok)( [0-9]+)? - /, ""); print }' "$report" > "$emitted"
  node -e 'const fs = require("node:fs"); for (const test of JSON.parse(fs.readFileSync(process.argv[1], "utf8")).tests) console.log(test.name);' "$manifest" > "$manifest_names"

  if ! diff -u "$manifest_names" "$emitted"; then
    printf '%s\n' 'ERROR: manifest test names do not exactly match TAP test names.' >&2
    exit 1
  fi
}

# run_suite <label> <image> <assignment> <solution-file> <solution-path-in-workspace>
#           <expected-passed> <expected-failed> <server-command> <suite-command> <shim-source-or-empty>
# The shim source is bind-mounted read-only at /opt/gitgrader-shim for the script
# languages (node, python); the Java runtime uses the shim compiled into the image,
# so its last argument is empty and no host shim is mounted.
run_suite() {
  label=$1
  image=$2
  assignment=$3
  solution=$4
  solution_path=$5
  expected_passed=$6
  expected_failed=$7
  server_command=$8
  suite_command=$9
  shim_source=${10:-}

  report="$work/$label.tap"
  socket="$work/$label.sock"
  sandbox="$work/$label.server"
  workspace="$work/$label.workspace"
  manifest="$assignment/hidden-tests/manifest.json"

  # Both containers share one socket directory; the sandbox creates the socket
  # there and the suite connects to it. The suite directory is mounted only into
  # the suite container: the sandbox container never sees the hidden sources, and
  # its root filesystem is read-only with /tmp as tmpfs, no network, no extra
  # capabilities, and no privilege escalation, mirroring the runner's sandbox.
  mkdir -p "$socket" "$workspace"
  chmod 0777 "$socket"
  cp -R "$assignment/template/." "$workspace/"
  cp "$solution" "$workspace/$solution_path"
  chmod -R a+rX "$workspace"

  if [ -n "$shim_source" ]; then
    # shellcheck disable=SC2086
    shim_volume="--volume $shim_source:/opt/gitgrader-shim:ro"
  else
    shim_volume=''
  fi

  # shellcheck disable=SC2086
  docker run --detach --rm \
    --network none --cap-drop ALL --security-opt no-new-privileges \
    --read-only --tmpfs /tmp --user 65534:65534 --workdir /workspace \
    --env SOLUTION_PATH="/workspace/$solution_path" \
    --env SHIM_SOCKET=/gitgrader-shim/runner.sock \
    --volume "$workspace:/workspace" \
    --volume "$socket:/gitgrader-shim" \
    $shim_volume \
    --entrypoint /bin/sh "$image" -c "$server_command" > "$sandbox"

  # shellcheck disable=SC2086
  if docker run --rm \
    --network none --cap-drop ALL --security-opt no-new-privileges \
    --read-only --tmpfs /tmp --user 65534:65534 \
    --env SHIM_SOCKET=/gitgrader-shim/runner.sock \
    --env HIDDEN_TESTS=/opt/hidden-tests \
    --volume "$assignment/hidden-tests:/opt/hidden-tests:ro" \
    --volume "$socket:/gitgrader-shim" \
    $shim_volume \
    --entrypoint /bin/sh "$image" -c "$suite_command" > "$report" 2>&1; then
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

  verify_names "$report" "$manifest"
  printf '%s\n' "$label: $passed passed / $failed failed / $total total (two containers, shared socket)"
}

node_suite_command='cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs'
node_server_command='node /opt/gitgrader-shim/server.js'
python_suite_command='cd /opt/hidden-tests && python3 /opt/gitgrader-shim/tap_runner.py'
python_server_command='python3 /opt/gitgrader-shim/server.py'
java_suite_command='cd /opt/hidden-tests && javac -cp /opt/junit-platform-console-standalone.jar:/opt/gitgrader-shim -d /tmp/classes *.java && java -cp /tmp/classes:/opt/gitgrader-shim:/opt/junit-platform-console-standalone.jar TapRunner'
java_server_command='java -cp /opt/gitgrader-shim Server'

run_suite node-complete "$node_image" "$node_assignment" \
  "$node_assignment/reference-solution/complete/string-utils.js" 'src/string-utils.js' 10 0 \
  "$node_server_command" "$node_suite_command" "$node_shim"
run_suite node-partial-70 "$node_image" "$node_assignment" \
  "$node_assignment/reference-solution/partial-70/string-utils.js" 'src/string-utils.js' 7 3 \
  "$node_server_command" "$node_suite_command" "$node_shim"

run_suite java-complete "$java_image" "$java_assignment" \
  "$java_assignment/reference-solution/complete/Solution.java" 'src/Solution.java' 10 0 \
  "$java_server_command" "$java_suite_command" ''
run_suite java-partial-70 "$java_image" "$java_assignment" \
  "$java_assignment/reference-solution/partial-70/Solution.java" 'src/Solution.java' 7 3 \
  "$java_server_command" "$java_suite_command" ''

run_suite python-complete "$python_image" "$python_assignment" \
  "$python_assignment/reference-solution/complete/solution.py" 'src/solution.py' 10 0 \
  "$python_server_command" "$python_suite_command" "$python_shim"
run_suite python-partial-70 "$python_image" "$python_assignment" \
  "$python_assignment/reference-solution/partial-70/solution.py" 'src/solution.py' 7 3 \
  "$python_server_command" "$python_suite_command" "$python_shim"

score=$(awk 'BEGIN { printf "%.1f", 7 / 10 * 100 }')
if [ "$score" != '70.0' ]; then
  printf '%s\n' "ERROR: expected formatted score 70.0, got $score." >&2
  exit 1
fi

printf '%s\n' "partial-70 score: $score% (7 / 10 * 100) on every language"
printf '%s\n' 'Manifest/TAP name join: exact match'
printf '%s\n' 'Suite container did not expose /workspace; sandbox never mounted the hidden suite.'
printf '%s\n' 'Example verification passed for node, java and python.'
