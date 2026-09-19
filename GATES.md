# Acceptance gates: issue #40 — hidden-suite confidentiality

Applies to the two-container remediation (option B) of
[#40](https://github.com/git-grader/gitgrader/issues/40), implemented in four
phases. Each phase ships as one commit, pushed to main, with its gates checked
before the commit.

OWNS: each phase's gates are owned by that phase; later phases may only rely on
gates already passing, never reopen them.

Scope: `DockerGradingRunner` and its test suite, the grading request/runtime
contract, the Node 24 runtime packaging and example assignment, and the
security documentation. Frontend and queue semantics are untouched.

Decisions locked before Phase 1 (per the plan's "Decide before any coding"):

- `SOLUTION_PATH` stays the environment seam; the shim server reads it and
  defaults to `/workspace/src/string-utils.js` when unset (the backend does not
  set it today, so no Java change is needed for this).
- A submission that never connects, or whose server dies mid-suite, fails its
  tests with a `connect-timeout` / `connection-closed` shim error rather than
  hanging T: the client retries the connect for `SHIM_CONNECT_TIMEOUT_MS`
  (default 30s) and the server bounds each call with `SHIM_CALL_TIMEOUT_MS`
  (default 30s). Outcome is failed/absent tests, the same class as a solution
  that cannot be loaded today; the runner's single timeout still bounds the
  pair.
- T runs the same digest-pinned runtime image as S, so `runtimeImageDigest`
  stays singular in the request and the shim client path is baked into the image.
- The shared socket volume is a per-run named volume mounted at
  `/gitgrader-shim`, with the directory created and owned 65534:65534 in the
  image so both containers can create/connect the socket without host
  permission surgery.

## Phase 1 — protocol and Node shim

- [x] P1-G1: `docs/grading-runtime-protocol.md` exists and pins the wire
  contract: NDJSON over a Unix socket, request `{id, method, args}`, response
  `{id, result}` or `{id, error:{phase,message}}`, the allowed JSON value types,
  the error `phase` vocabulary, and the statement that a stack is never
  transmitted (instructor-only).
  - CHECK: file exists and defines the value contract and the error envelope.
  - EVIDENCE: `docs/grading-runtime-protocol.md` written with transport,
    messages, value contract, phase table and "a stack trace is never
    transmitted".
- [x] P1-G2: `deployment/runtimes/node-24/shim/` ships `server.js`, `client.js`
  and a shared serialisation guard; the client exposes an async proxy whose
  calls are all `await`ed, and both sides reject non-JSON values (functions,
  class instances, BigInt, `undefined`, `NaN`/`Infinity`, cycles) with a shim
  error instead of silently mangling them.
  - CHECK: `node --test deployment/runtimes/node-24/shim/` passes.
  - EVIDENCE: `node --test` reports 10/10 pass; `assertJsonSerializable` walks
    the value instead of stringifying so NaN/Infinity/undefined fail loudly.
- [x] P1-G3: shim unit tests, independent of Docker, cover serialisation (both
  directions), error propagation, unknown-member and non-callable member calls,
  concurrent calls with id correlation, connect-timeout and connection-closed
  lifecycle, and a real server+client round trip over a Unix socket in a
  temporary directory.
  - CHECK: `node --test deployment/runtimes/node-24/shim/` passes; every case
    listed is asserted.
  - EVIDENCE: 10 cases in `shim.test.js` covering every listed scenario; all
    pass in under 0.5s.
- [x] P1-G4: the shim tests run inside the repo's only full verify, so CI
  enforces them (`./mvnw -B -Plicense clean verify` executes `npm run test:shim`
  through the frontend node toolchain).
  - CHECK: `./mvnw -B -Plicense clean verify` completes with BUILD SUCCESS
    locally (Docker-dependent tests excluded as today).
  - EVIDENCE: `npm run test:shim` passes from `frontend/`; full
    `./mvnw -B -Plicense clean verify` BUILD SUCCESS (04:10, coverage gates met,
    350 classes scanned by forbiddenapis). The `npm-test-shim` execution is
    bound to the test phase of the default-on frontend profile.
- [x] P1-G5: no behaviour shipped in Phase 1 reaches the runner yet — the
  single-container runner, request contract and example are unchanged.
  - CHECK: `git diff --stat` touches only the protocol doc, the shim package and
    the CI wiring.
  - EVIDENCE: only `backend/pom.xml`, `frontend/package.json`,
    `deployment/runtimes/node-24/shim/*`, `docs/grading-runtime-protocol.md`
    and `GATES.md`; runner/request/example byte-identical (two independent
    unstaged compose edits excluded from the commit).

## Phase 2 — runner topology

- [ ] P2-G1: `DockerGradingRunner` creates the shared per-run socket volume and
  starts two containers: S first (workspace rw, no hidden suite, shim command),
  then T once the socket is connectable (hidden suite ro + shim client + the
  runtime entry point). The hidden suite is never mounted in S.
  - CHECK: config test asserts the two bind/volume sets; a mocked execute test
    asserts S starts before T.
  - EVIDENCE: pending
- [ ] P2-G2: only T's stdout is parsed as the TAP report; S's own output is
  captured for diagnostics but never reaches `TapReportParser`.
  - CHECK: parity test drives both streams and asserts the result is built from
    T alone.
  - EVIDENCE: pending
- [ ] P2-G3: teardown kills both containers on every exit path — success,
  timeout, interrupt, log-drain failure, and container-start failure — and the
  shared volume is removed.
  - CHECK: mocked execute tests walk each path and assert kill+remove for both
    ids plus volume removal.
  - EVIDENCE: pending
- [ ] P2-G4: timeout and lease semantics are unchanged: one overall timeout
  bounds the pair; a timeout reports TIMEOUT exactly as the single-container run
  did.
  - CHECK: timeout execute test kills both containers and returns the same
    timeout result shape as today.
  - EVIDENCE: pending
- [ ] P2-G5: `DockerGradingRunnerIT`-style Docker tests assert S cannot list,
  read, or guess the hidden suite (mount/read checks from inside S fail) and
  that neither S nor T has network.
  - CHECK: `DockerGradingRunnerIT` (Docker-enabled) passes against the example
    runtime image.
  - EVIDENCE: pending
- [ ] P2-G6: a submission that calls `Function.prototype.toString` on the proxy
  or walks `require.cache` / the module registry gets nothing hidden (the
  solution lives in S; the suite in T).
  - CHECK: leak IT asserts the returned material contains no assertion or expected
    value from the hidden suite.
  - EVIDENCE: pending
- [ ] P2-G7: full verify still green (existing unit tests updated for the new
  topology, keeper coverage intact).
  - CHECK: `./mvnw -B -Plicense clean verify` BUILD SUCCESS.
  - EVIDENCE: pending (before commit)

## Phase 3 — contract, packaging, example

- [ ] P3-G1: `RuntimeView`, `NewRuntime`, `RuntimeDefinition` and
  `GradingExecutionRequest` carry the runtime's shim kind; the runner derives S's
  command from it instead of running `testCommand` in the sandbox, and
  `HIDDEN_TESTS` stops naming the suite from the submission's point of view
  (`SHIM_SOCKET` joins the environment).
  - CHECK: unit tests assert the request round-trips the new fields and the env
    split between S and T.
  - EVIDENCE: pending
- [ ] P3-G2: `RunnerRequestGuard` and `RemoteGradingRunner` sanitise and forward
  the new request shape so the remote-runner deployment cannot regress to a
  single shared process.
  - CHECK: guard/remote tests cover the new fields.
  - EVIDENCE: pending
- [ ] P3-G3: suite publication requires the shim harness: a suite published
  without it is refused (same rule as a missing `manifest.json`); versioning in
  the existing `tests/<suite>/<version>/` layout is unchanged.
  - CHECK: run the suite-publish path against a harness-less suite and assert a
    refusal; unit test asserts the rule.
  - EVIDENCE: pending
- [ ] P3-G4: the Node example is migrated — `hidden.test.js` awaits the proxy,
  `manifest.json` is unchanged, `verify-example.sh` exercises the two-container
  path, the runtime `Dockerfile` bakes the shim (with the preried
  65534-owned socket dir) and its README documents the S/T contract.
  - CHECK: `examples/verify-example.sh` passes; built runtime image contains the
    shim at the baked path.
  - EVIDENCE: pending
- [ ] P3-G5: `scripts/install.sh`, `examples/seed-data.sql`, and
  `docs/manual-testing.md` + `docs/e2e-test.md` describe the shim contract
  accurately (no single-shared-process wording remains).
  - CHECK: grep finds no stale container/shared-process claims in the touched
    docs.
  - EVIDENCE: pending
- [ ] P3-G6: full verify still green including frontend, license, and shim tests.
  - CHECK: `./mvnw -B -Plicense clean verify` BUILD SUCCESS.
  - EVIDENCE: pending (before commit)

## Phase 4 — guarantee and docs

- [ ] P4-G1: `docs/security.md` replaces the "not confidential from the
  submission" paragraph with the new boundary (S cannot read T; T's process
  still holds the hazards the manifest addresses: undeclared tests and forged
  passes are ruled out exactly as before), and closes the "tracked in issue #40"
  note.
  - CHECK: `grep -n "not confidential" docs/security.md` returns nothing; the
    boundary section names S and T.
  - EVIDENCE: pending
- [ ] P4-G2: the issue's acceptance criteria map to implemented, verified facts:
  (a) S cannot read hidden sources during grading — P2-G5/G6; (b) the student
  result shows the same outcomes — P2-G2 plus unchanged `TapReportParser`
  pathway; (c) security doc describes the boundary — P4-G1.
  - CHECK: the summary cites gate evidence for each criterion.
  - EVIDENCE: pending
- [ ] P4-G3: the marker wording is updated: a hidden suite is now unreadable by a
  determined student, which is the stronger property.
  - CHECK: the security boundary paragraph states the stronger property.
  - EVIDENCE: pending
- [ ] P4-G4: full verify still green and the issue is closed.
  - CHECK: `./mvnw -B -Plicense clean verify` BUILD SUCCESS; issue #40 closed
    with the plan-comment referenced.
  - EVIDENCE: pending (after push)

## Abandoned

None yet.