# Grader test-suite contract

This directory is stored outside the student project and mounted read-only at
`/opt/hidden-tests` in the suite container of a shimmed round. The submission is
graded in a separate sandbox container that never sees these hidden sources;
the suite reaches the submission over the shim socket that the two containers
share. `test_solution.py` opens that channel as its first action:

```python
from client import create_shim_client

proxy, close = create_shim_client()
```

The proxy below the socket exposes the sandbox container's module, so each test
calls the submission's functions through `proxy` instead of importing a
`SOLUTION_PATH` directly. The grader sets `SHIM_SOCKET` to the shared socket
for both containers of a shimmed round; without that wiring the suite could not
test anything, so refusing it is an infrastructure error rather than a mark.

The runtime image bakes the shim into `/opt/gitgrader-shim` and pytest into the
interpreter, and puts the shim on `PYTHONPATH`, so the import above needs no
install step. `tap_reporter.py` is the pytest plugin the runtime bakes in: the
suite command `python -m pytest -p tap_reporter --gitgrader-tap
-p no:cacheprovider -q` emits TAP on standard output for the backend to parse.

`manifest.json` is required, and it is what decides which tests exist: the
grader records one result per declared test and discards output naming anything
else, because a submission shares standard output with the reporter and can
print convincing test lines of its own. A test whose emitted name does not match
its manifest entry is scored as never having run, so keep the two in step. The
reporter names each test after the test function's last `::` segment, so
manifest entries are the bare function names (`test_h01_...`), not node ids.
