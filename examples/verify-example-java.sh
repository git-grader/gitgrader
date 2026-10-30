#!/bin/sh
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -eu

# CDPATH is cleared so a user's CDPATH cannot redirect the cd.
# shellcheck disable=SC1007
root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
# shellcheck disable=SC1007
repo=$(CDPATH= cd -- "$root/.." && pwd)
assignment="$root/assignments/assignment-03-geo-utils"
suite="$assignment/hidden-tests"
manifest="$suite/manifest.json"
shim_classes=$repo/deployment/runtimes/java-shim
work=$(mktemp -d "${TMPDIR:-/tmp}/gitgrader-example-java.XXXXXX")

# Both halves run the locally built runtime image. The stock Liberica JDK base
# cannot stand in: it has no Maven and no baked shim classes, so the suite
# container could not build anything. Override JAVA_IMAGE only with an
# equivalent image built from the same Dockerfile.
java_image=${JAVA_IMAGE:-gitgrader-java-27:local}

for tool in docker python3 mktemp; do
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
  # The sandbox compiled as uid 65534, so the workspace holds nobody-owned
  # class files the host user cannot remove; a final container with the same
  # mount and root privileges clears what the round left behind.
  if [ -d "$work/workspace" ]; then
    docker run --rm -v "$work/workspace:/workspace" busybox sh -c 'rm -rf /workspace/* /workspace/.[!.]* 2>/dev/null' >/dev/null 2>&1 || true
  fi
  rm -rf "$work"
}
trap cleanup EXIT HUP INT TERM

if ! docker image inspect "$java_image" >/dev/null 2>&1; then
  printf '%s\n' "ERROR: runtime image $java_image is not present locally." >&2
  printf '%s\n' "Build it with:  deployment/runtimes/build-runtimes.sh" >&2
  exit 1
fi

verify_names() {
  report=$1
  emitted="$work/emitted-names.txt"
  manifest_names="$work/manifest-names.txt"

  # Surefire writes one <testcase name="..."> per test method; the suite
  # command printed the XML on standard output for the backend to parse.
  python3 - "$report" > "$emitted" <<'PYEOF'
import re
import sys

xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()
for name in re.findall(r'<testcase\b[^>]*\bname="([^"]*)"', xml):
    print(name)
PYEOF
  python3 -c 'import json, sys; [print(t["name"]) for t in json.load(open(sys.argv[1]))["tests"]]' "$manifest" > "$manifest_names"

  sort "$manifest_names" > "$work/manifest-sorted.txt"
  sort "$emitted" > "$work/emitted-sorted.txt"
  if ! diff -u "$work/manifest-sorted.txt" "$work/emitted-sorted.txt"; then
    printf '%s\n' 'ERROR: manifest test names do not exactly match JUnit XML testcase names.' >&2
    exit 1
  fi
}

run_suite() {
  label=$1
  solution=$2
  expected_passed=$3
  expected_failed=$4
  report="$work/$label.xml"
  socket="$work/$label.sock"
  sandbox="$work/$label.server"

  # Both containers share one socket directory; the sandbox compiles the
  # submission and starts the shim server there, and the suite connects to it.
  # The suite directory is mounted only into the suite container: the sandbox
  # container never sees the hidden sources, and both root filesystems are
  # read-only with /tmp as tmpfs, no network, no extra capabilities, and no
  # privilege escalation, mirroring the runner's sandbox.
  mkdir -p "$socket" "$work/workspace/src"
  chmod 0777 "$socket"
  # A previous round left nobody-owned class files here; a root container
  # clears the workspace so this round compiles its own solution and the
  # host's chmod does not trip over files it does not own.
  docker run --rm -v "$work/workspace:/workspace" busybox sh -c 'rm -rf /workspace/src /workspace/target 2>/dev/null' >/dev/null 2>&1 || true
  mkdir -p "$work/workspace/src"
  cp -R "$assignment/template/." "$work/workspace/"
  cp "$solution" "$work/workspace/src/Solution.java"
  # The sandbox runs as an unprivileged user and must compile into the
  # workspace (the shim loads classes beside the sources), so the tree is
  # writable, not just readable.
  chmod -R a+rwX "$work/workspace"

  # The sandbox compiles the submitted source beside itself (the shim's class
  # loader reads the parent directory of SOLUTION_PATH) and serves it. This
  # mirrors the runtime row's shim command.
  docker run --detach --rm \
    --network none --cap-drop ALL --security-opt no-new-privileges \
    --read-only --tmpfs /tmp --user 65534:65534 --workdir /workspace \
    --env SOLUTION_PATH=/workspace/src/Solution.java \
    --env SHIM_SOCKET=/gitgrader-shim/runner.sock \
    --volume "$work/workspace:/workspace" \
    --volume "$socket:/gitgrader-shim" \
    --entrypoint /bin/sh "$java_image" -c 'javac -d /workspace/src /workspace/src/*.java && java -cp /opt/gitgrader-shim/classes gitgrader.ShimServer' > "$sandbox"

  # The suite command mirrors the runtime row: copy the read-only suite to
  # /tmp so Maven has somewhere to write, build and test offline, then print
  # the Surefire XML on standard output for the backend's parser. The image's
  # baked MAVEN_OPTS already points the local repository at /opt/maven-repository.
  # Unlike a TAP runner, Maven exits 0 even with failing tests once
  # maven.test.failure.ignore is set (the XML, not the exit code, is the
  # report the backend parses). Only the pass/fail counts are asserted; the
  # exit status of the suite container is recorded but not enforced.
  docker run --rm \
    --network none --cap-drop ALL --security-opt no-new-privileges \
    --read-only --tmpfs /tmp --user 65534:65534 \
    --env SHIM_SOCKET=/gitgrader-shim/runner.sock \
    --volume "$suite:/opt/hidden-tests:ro" \
    --volume "$socket:/gitgrader-shim" \
    --entrypoint /bin/sh "$java_image" -c 'cp -R /opt/hidden-tests /tmp/suite && cd /tmp/suite && mvn --offline --batch-mode --quiet test && cat target/surefire-reports/*.xml' > "$report" 2>&1 || true

  docker rm -f "$(cat "$sandbox")" >/dev/null 2>&1 || true

  # Surefire XML carries totals as tests=/failures=/errors= on <testsuite>.
  python3 - "$report" > "$work/summary.txt" <<'PYEOF'
import re
import sys

xml = open(sys.argv[1], encoding="utf-8", errors="replace").read()

def total(attr):
    return sum(int(m) for m in re.findall(r'<testsuite\b[^>]*\b' + attr + r'="(\d+)"', xml))

tests = total("tests")
failures = total("failures") + total("errors")
skipped = total("skipped")
print(tests - failures - skipped)
print(failures)
print(tests)
PYEOF
  passed=$(sed -n 1p "$work/summary.txt")
  failed=$(sed -n 2p "$work/summary.txt")
  total=$(sed -n 3p "$work/summary.txt")

  if [ "$passed" != "$expected_passed" ] || [ "$failed" != "$expected_failed" ] || [ "$total" != 10 ]; then
    printf '%s\n' "ERROR: $label expected $expected_passed passed / $expected_failed failed / 10 total; got ${passed:-missing} / ${failed:-missing} / ${total:-missing}." >&2
    cat "$report" >&2
    exit 1
  fi

  verify_names "$report"
  printf '%s\n' "$label: $passed passed / $failed failed / $total total (two containers, shared socket)"
}

run_suite complete "$assignment/reference-solution/complete/Solution.java" 10 0
run_suite partial-70 "$assignment/reference-solution/partial-70/Solution.java" 7 3

score=$(awk 'BEGIN { printf "%.1f", 7 / 10 * 100 }')
if [ "$score" != '70.0' ]; then
  printf '%s\n' "ERROR: expected formatted score 70.0, got $score." >&2
  exit 1
fi

printf '%s\n' "partial-70 score: $score% (7 / 10 * 100)"
printf '%s\n' 'Manifest/JUnit-XML name join: exact match'
printf '%s\n' 'Suite container did not expose /workspace; sandbox never mounted the hidden suite.'
printf '%s\n' 'Java example verification passed.'
