# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""TAP runner for Python hidden suites, the Python counterpart of the Java
shim's TapRunner.

pytest-tap's ``--tap-stream`` emits ``ok N <nodeid>`` with no ``- `` separator,
which the grading runner's TAP parser does not recognise, so the Python runtime
runs hidden suites through this script instead. It runs pytest in-process on the
current directory (``/opt/hidden-tests``), then prints one TAP line per declared
manifest test, in manifest order, using the exact ``name`` the manifest carries:

    ok 1 - h01 truncate preserves text at the maximum length

Each test function is named ``test_<id>`` (for example ``test_h01``) and is
joined to the manifest entry whose ``id`` is ``h01``. The plan and the
``# tests`` / ``# pass`` / ``# fail`` counts are printed after the test lines,
mirroring the Java TapRunner. Exit status is 0 only when every declared test
passed, so a failing submission still exits non-zero.
"""

import contextlib
import io
import json
import os
import sys

import pytest

MANIFEST_NAME = "manifest.json"


class _Collector:
    """Records the pass/fail outcome of each collected test by node id."""

    def __init__(self):
        self.results = {}

    def pytest_runtest_logreport(self, report):
        if report.when == "setup" and report.failed:
            self.results[report.nodeid] = False
        elif report.when == "call":
            self.results[report.nodeid] = report.passed


def main():
    root = os.getcwd()
    with open(os.path.join(root, MANIFEST_NAME), encoding="utf-8") as handle:
        manifest = json.load(handle)
    tests = manifest["tests"]

    collector = _Collector()
    # pytest writes its own progress to stdout; capture it so only this
    # runner's TAP reaches the grading parser on standard output.
    sink = io.StringIO()
    with contextlib.redirect_stdout(sink):
        pytest.main(["-p", "no:cacheprovider", "-q", "--rootdir", root, root], plugins=[collector])

    by_function = {}
    for node_id, passed in collector.results.items():
        by_function[node_id.split("::")[-1]] = passed

    total = len(tests)
    passed_count = 0
    for index, test in enumerate(tests, start=1):
        passed = by_function.get("test_" + test["id"], False)
        if passed:
            passed_count += 1
        status = "ok" if passed else "not ok"
        print(f"{status} {index} - {test['name']}")
    print(f"1..{total}")
    print(f"# tests {total}")
    print(f"# pass {passed_count}")
    print(f"# fail {total - passed_count}")
    sys.exit(0 if passed_count == total else 1)


if __name__ == "__main__":
    main()
