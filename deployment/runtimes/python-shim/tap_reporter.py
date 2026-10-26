# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""
A pytest plugin that reports each test as one GitGrader TAP line.

pytest names a test by its node id, which carries file paths the grading
manifest cannot join against: names there are the operator's own. This
reporter prints one line per test in the node-runner shape the backend
already reads (``ok 1 - <name>``), the name being the bare test function's
name so a manifest can stay close to what an instructor writes.

A failure's diagnostics are carried as a YAML block after the status line,
mirroring how node's ``--test`` runner reports a failure; the backend parser
reads the block as instructor-only detail. A test skipped anywhere - a skip
mark, a skip inside a fixture - is reported as a failure: a hidden check that
did not run is not a pass, and reporting it as one would let a template earn
marks for work it never did.
"""

from __future__ import annotations

import sys


def pytest_addoption(parser):
    group = parser.getgroup("terminal reporting", "GitGrader TAP reporting")
    parser.addoption(
        "--gitgrader-tap",
        action="store_true",
        dest="gitgrader_tap",
        default=False,
        help="report test results as TAP lines in the GitGrader shape",
    )


def pytest_configure(config):
    if not config.option.gitgrader_tap:
        return
    config.pluginmanager.register(GitGraderTapReporter(config), "gitgrader-tap-reporter")


def pytest_unconfigure(config):
    reporter = config.pluginmanager.getplugin("gitgrader-tap-reporter")
    if reporter is not None:
        config.pluginmanager.unregister(reporter)


def _test_name(report):
    """The manifest joins on the bare test name.

    A node id is ``path.py::TestClass::test_x``; the name the manifest
    declares is the function the instructor wrote, which is the node id's
    last segment.
    """
    return report.nodeid.rsplit("::", 1)[-1]


class GitGraderTapReporter:
    """Prints one TAP line per test in the shape the backend parser reads."""

    def __init__(self, config):
        self.config = config
        # A test reaches this reporter up to three times (setup, call,
        # teardown); only the first report wins, so a fixture failure and the
        # teardown that follows cannot emit a second line for the same test -
        # the backend refuses a test reported more than once.
        self._emitted = set()
        self._results = []

    def pytest_sessionstart(self, session):
        # The terminal reporter's progress dots interleave with the TAP
        # stream on stdout, and a stray dot glued to an "ok" line corrupts
        # the report the backend reads; the TAP output is the whole report.
        # The unregister happens here rather than in pytest_configure because
        # the built-in terminal reporter registers itself during its own
        # pytest_configure, which runs after this plugin's.
        terminal = self.config.pluginmanager.getplugin("terminalreporter")
        if terminal is not None:
            self.config.pluginmanager.unregister(terminal)
        print("TAP version 13", flush=True)

    def pytest_runtest_logreport(self, report):
        name = _test_name(report)
        if name in self._emitted:
            return

        if report.when == "call":
            self._emitted.add(name)
            self._emit(report.outcome == "passed", name, report)
        elif report.when == "setup" and (report.skipped or report.failed):
            # A test that never reached its call phase still happened in the
            # eyes of the suite; a setup failure is the test failing, and a
            # skipped fixture is the check not running, which is not a pass.
            self._emitted.add(name)
            self._emit(False, name, report)

    def pytest_sessionfinish(self, session, exitstatus):
        passed = sum(1 for ok, _name in self._results if ok)
        total = len(self._results)
        print(f"1..{total}", flush=True)
        print(f"# tests {total}", flush=True)
        print(f"# pass {passed}", flush=True)
        print(f"# fail {total - passed}", flush=True)
        if passed != total:
            sys.exit(1)

    def _emit(self, ok, name, report):
        status = "ok" if ok else "not ok"
        number = len(self._results) + 1
        print(f"{status} {number} - {name}", flush=True)
        if not ok:
            lines = str(report.longrepr).splitlines() if report.longrepr else []
            if lines:
                print("  ---", flush=True)
                for line in lines:
                    print(f"  {line}", flush=True)
                print("  ...", flush=True)
        self._results.append((ok, name))
