#!/bin/sh
set -eu

root=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
assignment="$root/assignments/assignment-01-string-utils"
suite="$assignment/hidden-tests"
manifest="$suite/manifest.json"
work=$(mktemp -d "${TMPDIR:-/tmp}/gitgrader-example.XXXXXX")

cleanup() {
  rm -rf "$work"
}
trap cleanup EXIT HUP INT TERM

verify_names() {
  report=$1
  emitted="$work/emitted-names.txt"
  manifest_names="$work/manifest-names.txt"

  awk '/^[[:space:]]*# Subtest: / { sub(/^[[:space:]]*# Subtest: /, ""); print }' "$report" > "$emitted"
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

  if SOLUTION_PATH="$solution" node --test --test-reporter=tap "$suite/hidden.test.js" > "$report" 2>&1; then
    status=0
  else
    status=$?
  fi

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
  printf '%s\n' "$label: $passed passed / $failed failed / $total total"
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
printf '%s\n' 'Example verification passed.'
