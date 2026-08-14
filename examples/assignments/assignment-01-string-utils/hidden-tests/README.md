# Grader test-suite contract

This directory is stored outside the student project and mounted read-only at
`/opt/hidden-tests` during grading. The runner launches this suite with
`SOLUTION_PATH` set to the absolute path of the implementation copied into
`/workspace`, for example:

```sh
SOLUTION_PATH=/workspace/src/string-utils.js \
  node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js
```

`hidden.test.js` defaults to `/workspace/src/string-utils.js` when the runner
does not supply `SOLUTION_PATH`. The grader uses Node's TAP reporter and joins
the emitted test names to `manifest.json` exactly. Do not expose this directory
to students or mount it below `/workspace`.

`manifest.json` is required, and it is what decides which tests exist: the
grader records one result per declared test and discards output naming anything
else, because a submission shares standard output with the reporter and can
print convincing test lines of its own. A test whose emitted name does not match
its manifest entry is scored as never having run, so keep the two in step.
