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
  | `SOLUTION_PATH` | `/workspace/src/string-utils.js` | server | submitted module to load |
  | `SHIM_CALL_TIMEOUT_MS` | `30000` | server | ceiling on one call |
  | `SHIM_CONNECT_TIMEOUT_MS` | `30000` | client | ceiling on the initial connect |

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

Only JSON values cross the socket, in both directions:

> `null` · boolean · finite number · string · array of JSON values · plain
> object whose own string-keyed values are JSON values

Everything else is **rejected with an error, never silently mangled**:
`undefined`, `NaN`, `Infinity`, functions, class instances, `Map`, `Set`,
`Date`, symbols, `bigint`, symbol-keyed properties, and circular structures.
A single `JSON.stringify` would instead turn `NaN`/`Infinity`/`undefined` into
JSON-safe artefacts and this is precisely the silent loss of information the
boundary exists to prevent; both sides validate with a structural walk, not a
stringify attempt.

Arguments are validated by the client before they are sent
(`non-serializable-args`) and defensively again by the server; a result is
validated by the server before it is sent (`non-serializable-result`). A
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
  `SHIM_CONNECT_TIMEOUT_MS` passes, which absorbs the short gap while S creates
  the socket.
- A submission whose process never appears, or whose server dies mid-suite,
  fails its tests with `connect-timeout` / `connection-closed` — the same
  outcome class as a solution that throws while loading today, and the run is
  still bounded by the runner's one overall timeout, which kills both
  containers.
- A submitted function that never returns fails the call with
  `invocation-timeout` after `SHIM_CALL_TIMEOUT_MS`, so one hung call cannot
  wedge the suite until the runner's timeout.
- The runner tears both containers down on every exit path and removes the
  shared socket volume with them.

## Implementing a runtime

The wire contract is language-independent. The shipped Node shim lives in
`deployment/runtimes/node-shim/` as `server.js` (loads `SOLUTION_PATH` and
serves exports) and `client.js` (an async `Proxy` of those exports where every
member is an awaited call), with `serializable.js` enforcing the value contract
and `phases.js` the vocabulary above. All Node runtimes (`node-22`,
`node-24`, `node-26`) share it; a new runtime ships a server and client for
its language; the suite side only ever completes `await`ed calls.

Run the shim's own tests, which need no Docker:

```sh
node --test "deployment/runtimes/node-shim/**/*.test.js"
```

The runner's full verify (`./mvnw -B -Plicense clean verify`) runs the same
suite through `npm run test:shim`.