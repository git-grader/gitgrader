# Grader test-suite contract

This directory is stored outside the student project and mounted read-only at
`/opt/hidden-tests` in the suite container of a shimmed round. The submission is
graded in a separate sandbox container that never sees these hidden sources;
the suite reaches the submission over the shim socket that the two containers
share. `hidden.test.js` opens that channel as its first action:

```js
import { createShimClient } from '/opt/gitgrader-shim/client.js';

const { proxy } = await createShimClient();
```

The proxy below the socket exposes the sandbox container's module exports, so
each test awaits the submission's functions through `proxy` instead of importing
a `SOLUTION_PATH` directly. The grader sets `SHIM_SOCKET` to the shared socket
for both containers of a shimmed round; without that wiring the suite could not
test anything, so refusing it is an infrastructure error rather than a mark.

`manifest.json` is required, and it is what decides which tests exist: the
grader records one result per declared test and discards output naming anything
else, because a submission shares standard output with the reporter and can
print convincing test lines of its own. A test whose emitted name does not match
its manifest entry is scored as never having run, so keep the two in step.