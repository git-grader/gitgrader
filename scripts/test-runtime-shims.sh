#!/usr/bin/env bash
# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

set -euo pipefail

# Every runtime shim has its own language's self-test suite, each needing no Docker.
# The Node shim runs through `npm run test:shim` (the way the Maven gate invokes it;
# the Python shim through pytest where the toolchain exists. The JDK's single-file
# source launcher runs the Java shim's self-test (the way the Java runtime's build
# runs it) when a JDK is available.

cd "$(dirname "$0")/.."

fail=0

# Node shim: node --test over the node-shim directory, the same command the Maven
# build runs through `npm run test:shim`.
if command -v node >/dev/null 2>&1; then
  node --test "deployment/runtimes/node-shim/**/*.test.js"
else
  echo "node is not installed; skipping the Node shim suite" >&2
fi

# Python shim: pytest over the python-shim directory.
if command -v python3 >/dev/null 2>&1; then
  ( cd deployment/runtimes/python-shim && python3 -m pytest -q )
else
  echo "python3 is not installed; skipping the Python shim suite" >&2
fi

# Java shim: the single-file source launcher runs its self-test with a bare JDK.
if command -v java >/dev/null 2>&1; then
  java deployment/runtimes/java-shim/ShimSelfTest.java
else
  echo "java is not installed; skipping the Java shim suite" >&2
fi

exit "$fail"
