# Grader test-suite contract

This directory is stored outside the student project and mounted read-only at
`/opt/hidden-tests` in the suite container of a shimmed round. The submission is
graded in a separate sandbox container that never sees these hidden sources;
the suite reaches the submission over the shim socket that the two containers
share. `test_hidden.py` opens that channel as its first action:

```python
import sys
sys.path.insert(0, "/opt/gitgrader-shim")
from client import create_shim_client

shim = create_shim_client()
```

Each `shim.<function>(...)` call invokes one of the submission's module-level
functions in the sandbox and returns its result. The grader sets `SHIM_SOCKET`
to the shared socket for both containers of a shimmed round; without that
wiring the suite could not test anything, so refusing it is an infrastructure
error rather than a mark.

`manifest.json` is required, and it is what decides which tests exist: the
grader records one result per declared test and discards output naming anything
else, because a submission shares standard output with the reporter and can
print convincing test lines of its own. The runtime runs the suite through
`python3 /opt/gitgrader-shim/tap_runner.py`, which prints one
`ok N - <name>` / `not ok N - <name>` line per declared test in manifest order;
each test function is named `test_<id>` and joined to the manifest entry whose
`id` is `<id>`, so keep the two in step.
