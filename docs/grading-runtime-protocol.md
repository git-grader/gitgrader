# Grading runtime protocol

The submission and the hidden suite must not share an interpreter: a test that
runs the submitted code in the same process can read that code's assertions and
expected values from the function objects themselves, whatever the filesystem
looks like. The runner therefore starts **two containers**:

- **S (sandbox)** — the submitted code, under the untrusted host's hardening
  (no network, read-only root, dropped capabilities, PID/memory/CPU limits).
  No part of the hidden suite is mounted here.
- **T (trusted)** — the operator's hidden suite and `manifest.json`, mounted
  read only, plus the report format's entry point. Only T's standard output is
  the TAP report, so the student-facing result is unchanged.

S and T communicate through **the shim**: a Unix socket on a volume shared only
by the two containers of one run, speaking newline-delimited JSON (NDJSON).
S starts first, loads the submitted module, and serves its exports over the
socket; T runs the suite against a client that turns every exported member into
an awaited call. A student's code can therefore not reach T's process, its
globals, its module registry, or its filesystem.

This document pins the wire contract so every runtime can implement it without
renegotiating the runner.

## Transport

- A Unix socket on the per-run shared volume, default path
  `/gitgrader-shim/runner.sock` and configured by the `SHIM_SOCKET`
  environment variable on both containers.
- Frame encoding: one JSON value per line (UTF-8), terminated by `\n`; no other
  bytes on the wire.
- The socket is created by S (the server) and connected by T (the client).
- Locations and limits are environment-driven, so the same code serves in the
  container and in local tests:

  | variable | default | read by | meaning |
  | --- | --- | --- | --- |
  | `SHIM_SOCKET` | `/gitgrader-shim/runner.sock` | both | socket path |
  | `SOLUTION_PATH` | held by the server as `/workspace/src/string-utils.js` | server | submitted module to load |
  | `SHIM_CALL_TIMEOUT_MS` | `30000` | server | ceiling on one call |
  | `SHIM_CONNECT_TIMEOUT_MS` | `30000` | client | ceiling on the initial connect |

When `SOLUTION_PATH` names an existing file it is used as-is; otherwise the
server loads the single `.js` file directly under `/workspace/src`, so a
zero-dependency assignment needs no per-assignment configuration. The server
refuses to guess (and asks for an explicit `SOLUTION_PATH`) when that directory
holds several modules.

## Messages

A **request** has exactly `id`, `method` and `args`:

```json
{"id": 1, "method": "capitalize", "args": ["x"]}
```

A **successful response** carries the same `id` and the serialised `result`
(the submitted function's return value):

```json
{"id": 1, "result": "X"}
```

A **failed response** carries the same `id` and an error envelope:

```json
{"id": 1, "error": {"phase": "invocation-error", "message": "boom"}}
```

`id` is a number chosen by the client. Responses may arrive in any order; the
client correlates by `id`, never by stream order, because S serves concurrent
calls.

A malformed line that cannot even be framed (invalid JSON, missing `id` or
`method`, `args` that is not an array) is answered with
`{"error": {"phase": "bad-request", "message": "..."}}` and no `id`, so the
well-behaved client always ignores it rather than misattributing it.

## The value contract

Values still travel as JSON on the wire, in both directions:

> `null` · boolean · finite number · string · array of values · plain
> object whose own string-keyed values are values

A small set of values that JSON cannot carry is transported exactly because a
structured codec, not a bare `JSON.stringify`, decides what goes on the line.
Both sides encode and decode with the same structural walk; the encoded
representation stays plain JSON at every point:

| JavaScript value | wire representation |
| --- | --- |
| `bigint` | `{ "$gitgrader": ["bigint", "123"] }` |
| `undefined` | `{ "$gitgrader": ["undefined"] }` |
| object with a non-plain prototype | `{ "$gitgrader": ["proto-object", <encoded prototype>, <encoded own fields>] }` |

The marker object is an object with the single reserved own key `$gitgrader`;
it is decoded back into the original value and is never delivered as such. The
`proto-object` form is rebuilt on the suite side with `Object.create`, so the
result of an exercise like «attach a prototype with `Object.create`» keeps its
behaviour (`obj.category` resolving through the chain) instead of being refused.

Everything else is still **rejected with an error, never silently mangled**:
`NaN`, `Infinity`, functions, symbols, symbol-keyed members, class instances
(`Date`, `Map`, `Set`, `RegExp`, typed arrays, ...), and circular structures.
A single `JSON.stringify` would turn `NaN`/`Infinity`/`undefined` into
JSON-safe artefacts or drop symbol keys, which is precisely the silent loss of
information the boundary exists to prevent; both sides validate with a
structural walk, not a stringify attempt.

Arguments are encoded by the client before they are sent
(`non-serializable-args`) and decoded defensively by the server; a result is
encoded by the server before it is sent (`non-serializable-result`). A
boundary violation fails exactly that call; the connection stays usable.

## Error envelope

Every error is `{"phase": <stable string>, "message": <human string>}`.

| phase | raised by | meaning |
| --- | --- | --- |
| `bad-request` | server | message not a well-formed request |
| `no-such-member` | server | the solution does not export that name |
| `not-callable` | server | the export exists but is not a function |
| `non-serializable-args` | client or server | arguments violate the value contract |
| `non-serializable-result` | server | the result violates the value contract |
| `invocation-error` | server | the submitted function threw |
| `invocation-timeout` | server | the call exceeded `SHIM_CALL_TIMEOUT_MS` |
| `connect-timeout` | client | no server appeared within `SHIM_CONNECT_TIMEOUT_MS` |
| `connection-closed` | client | the socket was lost while a call was pending |

A stack trace is **never transmitted**. The `invocation-error` message is the
thrown value's own text; file paths, line numbers and the submitted module's
internals stay on S's standard error, which the runner keeps as
instructor-only diagnostics and never parses as a report.

## Lifecycle and failure semantics

- The runner starts S first, then T. T's client retries its connect until
  `SHIM_CONNECT_TIMEOUT_MS` passes, absorbing the short gap while S creates the
  socket.
- A submission whose server never appears, or dies mid-suite, fails with
  `connect-timeout` / `connection-closed` — bounded by the runner's overall
  timeout, which kills both containers.
- A submitted function that never returns fails with `invocation-timeout` after
  `SHIM_CALL_TIMEOUT_MS`, so one hung call cannot wedge the suite.
- The runner tears both containers down on every exit path and removes the shared
  socket volume with them.

## Declaring a runtime's topology

Every runtime states which of the two topologies it grades with, because they
are not interchangeable and the difference shows up in a student's mark:

| `shimKind` | what the runner does |
| --- | --- |
| `legacy` | Runs the submission and the hidden suite in one sandbox. |
| a shim name, e.g. `node` | Runs them in two containers joined by the shim socket. |

A runtime that leaves `shimKind` unstated is rejected, because a blank value
silently picked a grading path. `legacy` is stored as an absent topology, so a
runtime created now grades exactly as one created before the shim existed, and
a suite that has not been adapted keeps working untouched. A runtime already in
the database reports an absent topology and continues to grade in one sandbox;
saving it from the admin page records the choice explicitly.

Naming a shim obliges the hidden suite to import the shim client. A suite that
never opens the socket cannot load the submission at all, so the runner refuses
such a pairing as a misconfiguration rather than reporting it as a failing
student.

## Implementing a runtime

The wire contract is language-independent. A new runtime ships a server and a
client for its language; the suite side only ever completes awaited calls.
Three shims ship today, one per language family:

### Node.js

The shipped Node shim lives in `deployment/runtimes/node-shim/` as `server.js`
(loads `SOLUTION_PATH` and serves exports) and `client.js` (an async `Proxy` of
those exports where every member is an awaited call), with `serializable.js`
implementing the value codec (`serialize`/`deserialize`) and `phases.js` the
vocabulary above. All Node runtimes (`node-22`, `node-24`, `node-26`) share it.

Run the shim's own tests, which need no Docker:

```sh
node --test "deployment/runtimes/node-shim/**/*.test.js"
```

The runner's full verify (`./mvnw -B -Plicense clean verify`) runs the same
suite through `npm run test:shim`.

### Python

The Python shim lives in `deployment/runtimes/python-shim/`. `server.py` loads
the module named by `SOLUTION_PATH` (default `/workspace/src/solution.py`; with
no explicit path it takes the single module under `/workspace/src`) and serves
the module's public functions; `client.py`'s `create_shim_client()` returns a
proxy whose every attribute access becomes an awaited call. `serializable.py`
implements the value codec, `phases.py` the vocabulary above.

Python's value contract matches the table above with native mappings: `None`
for null, `int`/`float` for numbers, `list` and `dict` for arrays and plain
objects. A non-serialisable value (a function, a class instance beyond a plain
dict, a circular structure) is refused with `non-serializable-args` or
`non-serializable-result`, never mangled.

The image bakes the shim into `/opt/gitgrader-shim` and puts it on
`PYTHONPATH`, so a hidden suite imports `client` directly. The image also bakes
pytest plus the `tap_reporter` plugin: pytest does not emit the TAP shape the
backend parses (the `pytest-tap` plugin names tests without the ` - ` separator
and pytest's own progress dots interleave with the stream), so the runtime ships
a small custom reporter. It prints one `ok N - <name>` line per test, names
each test after its node id's last segment (the bare function name, which is
what the manifest declares), reports a skipped test as failed (a hidden check
that did not run is not a pass), and keeps pytest's progress output off stdout
by replacing the terminal reporter with a silent stand-in - unregistering it
outright crashes pytest's assertion-explanation machinery, which dereferences
the reporter while rendering a failure's detail.

The suite command is
`cd /opt/hidden-tests && python -m pytest -p tap_reporter --gitgrader-tap -p no:cacheprovider -q`,
and its stdout is the TAP report.

### Java

The Java shim lives in `deployment/runtimes/java-shim/`. `ShimServer` loads the
compiled solution class and serves each of its **static public** methods;
`ShimClient.connect(null, 0)` returns a client whose `call(name, args)` invokes
one over the socket. `Serializable` implements the value codec, `Phases` the
vocabulary above. The suite compiles against the client and calls the
submission's methods through it; the sandbox side compiles the submitted
sources beside themselves (the class loader reads the parent directory of
`SOLUTION_PATH`) before the server starts.

The image builds on Liberica JDK 27 and bakes Maven plus the JUnit 5 stack:
the priming step runs a real `mvn test` on a warmup project shaped like an
operator suite (same pinned compiler and Surefire plugin versions, one dummy
JUnit test), because `dependency:go-offline` alone skips the lifecycle-default
plugins. The local repository is primed into the shared, world-readable
`/opt/maven-repository` (`MAVEN_OPTS` redirects it) rather than root's home,
since the grading containers run as an unprivileged user whose home does not
exist. The compiled shim is both baked at `/opt/gitgrader-shim/classes` and
installed into the repository as `gitgrader:gitgrader-shim:1.0.0`, so a hidden
suite's pom resolves the client like any other offline dependency.

Surefire writes the JUnit XML to files, but the backend parses the test run's
standard output, so the runtime's test command ends by printing the reports:
`cp -R /opt/hidden-tests /tmp/suite && cd /tmp/suite && mvn --offline --batch-mode --quiet test && cat target/surefire-reports/*.xml`.
The copy to `/tmp` gives Maven a writable tree, because `/opt/hidden-tests` is
mounted read-only. Surefire writes the bare method name into each `<testcase>`
`name` attribute, which is what the manifest declares. The parser in the
backend is `JUnitXmlReportParser`, which reads the same integrity rules as the
TAP parser: exactly one result per declared test, in manifest order, with
undeclared names discarded and a test reported twice never passing.