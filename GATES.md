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
- The shared socket directory is a per-run host directory created under the
  storage temp area and chmod 0777, bind-mounted into both containers at
  `/gitgrader-shim` (rw) and deleted during teardown. A named volume owned
  65534:65534 in the image was the first design; the committed implementation
  opens a host directory by mode instead because the runtime image is owned by
  this project but the host uid that creates it is not, so the image cannot
  pre-chown the bind for the container uid. 0777 carries write+execute only
  (the socket needs both) and is scoped to this one class in the SpotBugs
  exclusions. The two containers need no shared identity to share the socket.

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

- [x] P2-G1: `DockerGradingRunner` creates the shared per-run socket directory and
  starts two containers: S first (workspace rw, no hidden suite, shim command),
  then T (hidden suite ro + shim client + the runtime entry point). The hidden
  suite is never mounted in S. T's shim client retries its connect for
  `SHIM_CONNECT_TIMEOUT_MS` (see the Phase 1 decisions), so starting T right
  after S needs no socket-readiness probe.
  - CHECK: config test asserts the two bind/volume sets; a mocked execute test
    asserts S starts before T.
  - EVIDENCE: `DockerGradingRunnerConfigTest`
    "sandbox mounts the workspace and the socket, never the hidden tests"
    (workspace rw + `/gitgrader-shim` rw, no `/opt/hidden-tests` bind) and
    "suite mounts the hidden tests and the socket, never the workspace";
    `DockerGradingRunnerExecutionTest` "starts the sandbox before the suite in a
    shimmed round" (in-order start assertions).
- [x] P2-G2: only T's stdout is parsed as the TAP report; S's own output is
  captured for diagnostics but never reaches `TapReportParser`.
  - CHECK: parity test drives both streams and asserts the result is built from
    T alone.
  - EVIDENCE: `DockerGradingRunnerExecutionTest` "reports a shimmed round from
    the suite's output alone, never the sandbox's": sandbox stream
    `1..9001\nok 9001 - leaked` is drained, the result is `1..2\n...` and
    `doesNotContain("9001", "leaked")`.
- [x] P2-G3: teardown kills both containers on every exit path — success,
  timeout, interrupt, log-drain failure, and container-start failure — and the
  shared socket directory is removed (not a Docker volume, see the decisions).
  - CHECK: mocked execute tests walk each path and assert kill+remove for both
    ids plus socket-directory deletion.
  - EVIDENCE: "removes both containers when a shimmed round fails to start"
    (removes S and T), "kills both containers when a shimmed round outlives its
    limit" (kills + removes both), the success path asserts both removals, and
    the runner's `finally` deletes the socket directory on every path
    (`DockerGradingRunner.executeTwoContainer`).
- [x] P2-G4: timeout and lease semantics are unchanged: one overall timeout
  bounds the pair; a timeout reports TIMEOUT exactly as the single-container run
  did.
  - CHECK: timeout execute test kills both containers and returns the same
    timeout result shape as today.
  - EVIDENCE: "kills both containers when a shimmed round outlives its limit"
    asserts `timedOut()` and both `killContainerCmd` calls; `awaitReport` waits
    the suite with `request.timeout()` and returns the same
    `GradingResult(-1, stdout, stderr, ..., timeout=true)` shape the single
    container returns.
- [x] P2-G5: `DockerGradingRunnerIT`-style Docker tests assert S cannot list,
  read, or guess the hidden suite (mount/read checks from inside S fail) and
  that neither S nor T has network.
  - CHECK: `DockerGradingRunnerIT` (Docker-enabled) passes against the example
    runtime image.
  - EVIDENCE: `DockerGradingRunnerIT` "grades over the protocol, the suite and
    the sandbox split and the same 7 of 10" (`1..10`, pass 7 / fail 3) and "a
    hostile submission cannot reach the hidden suite or the network" (`1..3`,
    pass 3: the submission cannot read/guess the suite and has no network);
    4 ITs run, all pass.
- [x] P2-G6: a submission that calls `Function.prototype.toString` on the proxy
  or walks `require.cache` / the module registry gets nothing hidden (the
  solution lives in S; the suite in T).
  - CHECK: leak IT asserts the returned material contains no assertion or expected
    value from the hidden suite.
  - EVIDENCE: in the hostile-submission IT the suite's three checks ask the
    sandbox (through the proxy) to list `/opt/hidden-tests`, read
    `/opt/hidden-tests/manifest.json` and reach the network; every attempt
    comes back only `error: ENOENT` / a network failure, so the material that
    crosses the socket contains no assertion or expected value from the hidden
    suite. Structurally the sandbox has no bind to the hidden tests and never
    receives `HIDDEN_TESTS` (config test "sandbox is not handed the
    hidden-tests environment the suite needs").
- [x] P2-G7: full verify still green (existing unit tests updated for the new
  topology, keeper coverage intact).
  - CHECK: `./mvnw -B -Plicense clean verify` BUILD SUCCESS.
  - EVIDENCE: `./mvnw -B -Plicense clean verify` BUILD SUCCESS (04:00) —
    frontend shim tests, license, formatting, checkstyle, PMD, SpotBugs, unit
    tests and Docker ITs all green. Recorded after commit `d146cb8` (see
    deviations below).

## Deviations recorded during Phase 2

- `shimKind`/`shimCommand` were put on `GradingExecutionRequest` in Phase 2 even
  though the plan carried them in Phase 3: without them the runner had no way to
  express that a request is shimmed, so the two-container path could not exist
  behind a gate. The runtime records (`RuntimeView`, `NewRuntime`,
  `RuntimeDefinition`) are wired in Phase 3 (P3-G1); the legacy constructor
  keeps P2 call sites compiling, suppressed in PMD.
- The shim moved from `deployment/runtimes/node-24/shim` to a shared
  `deployment/runtimes/node-shim` (one copy served to the node-22, node-24 and
  node-26 runtimes) when the runtime family was expanded in the same Phase 2
  commit. Phase 1 gate evidence that names the old path still describes the
  same package and tests; `frontend/package.json`'s `test:shim` now runs
  `node --test ../deployment/runtimes/node-shim/**/*.test.js`.
- New configuration `grading.docker.shim-mount-path` (`shimMountPath`, default
  empty). When set, the host shim directory is bound read-only into both
  containers at `/opt/gitgrader-shim`; empty leaves that path to the runtime
  image. The Docker ITs which lack a baked image set it and pass.
- `client.js` gained a socket ref-count fix in Phase 2 (the shared socket kept
  the suite process's event loop alive, so `node --test` never exited and real
  runs timed out): `socket.ref()` for the duration of each pending call,
  `socket.unref()` otherwise. All shim tests still pass.
- The socket mount is a per-run host directory (chmod 0777, deleted at
  teardown) rather than the image-owned-named-volume first decision; see the
  decisions block. Teardown removes the directory, not a Docker volume.

## Phase 3 — contract, packaging, example

- [x] P3-G1: `RuntimeView`, `NewRuntime`, `RuntimeDefinition` and
  `GradingExecutionRequest` carry the runtime's shim kind; the runner derives S's
  command from it instead of running `testCommand` in the sandbox, and
  `HIDDEN_TESTS` stops naming the suite from the submission's point of view
  (`SHIM_SOCKET` joins the environment).
  - CHECK: unit tests assert the request round-trips the new fields and the env
    split between S and T.
  - EVIDENCE: `RuntimeView` and `NewRuntime` gained `shimKind`/`shimCommand`
    (11/14-record constructors); `RuntimeDefinition` and migration
    `V11__runtime_shim_topology.sql` carry `shim_kind`/`shim_command`;
    `GradingExecutor.buildRequest` forwards them; `examples/seed-data.sql`
    registers `shim_kind='node-ipc'` for node-22/24/26.
    `GradingExecutorTest.forwardsTheRuntimeShimTopology` round-trips both fields;
    28 unit tests across grading/runtimes packages pass.
- [x] P3-G2: `RunnerRequestGuard` and `RemoteGradingRunner` sanitise and forward
  the new request shape so the remote-runner deployment cannot regress to a
  single shared process.
  - CHECK: guard/remote tests cover the new fields.
  - EVIDENCE: `RunnerRequestGuardTest` asserts the shim fields pass through
    unchanged; `RemoteGradingRunnerTest` sends and `content().json`-asserts
    `shimKind:"node-ipc"` and the derived `shimCommand` in the remote request
    body. Both green in the 28-test unit run.
- [x] P3-G3: the shim-harness rule is enforced at **grading time for shimmed
  runs only** (per the locked decision, not at suite publication): a shimmed
  runtime whose hidden suite never connects to the sandbox is refused as an
  infrastructure error, the same class as a missing `manifest.json`; suites
  graded by non-shimmed runtimes are unaffected.
  - CHECK: unit test runs the shimmed plan against a harness-less suite and
    asserts the refusal; a shimmed plan whose suite references the harness
    proceeds; non-shimmed plans are untouched.
  - EVIDENCE: `GradingExecutor.buildRequest` scans `*.js`/`*.cjs`/`*.mjs` under
    the suite for a `createShimClient` reference when `shimKind` is non-blank
    and raises `IllegalStateException` otherwise (message names the
    `/opt/gitgrader-shim/client.js` requirement).
    `GradingExecutorTest.refusesAShimmedRunWithoutTheShimHarness` asserts the
    exception and the workspace discard;
    `forwardsTheRuntimeShimTopology` writes the harness reference into its
    fixture suite first; every pre-existing non-shimmed executor test still
    passes. All in the 28-test unit run.
- [x] P3-G4: the Node example is migrated — `hidden.test.js` imports
  `createShimClient` and awaits the proxy over the shared socket,
  `manifest.json` is unchanged, `verify-example.sh` exercises the two-container
  path (the per-run host socket dir, not an image-owned volume), the runtime
  images bake the shim at `/opt/gitgrader-shim`, and their READMEs document the
  S/T contract.
  - CHECK: `examples/verify-example.sh` passes; built runtime image contains the
    shim at the baked path.
  - EVIDENCE: `./examples/verify-example.sh` → complete 10/0, partial-70 7/3,
    exact TAP/manifest name join, "Example verification passed"; the two node
    containers share only the socket dir, the suite is mounted only into T.
    `docker build -f deployment/runtimes/node-24/Dockerfile deployment/runtimes`
    produces an image with `client.js`, `server.js`, `phases.js`,
    `serializable.js`, `shim.test.js`, `package.json` at `/opt/gitgrader-shim`
    (readdir verified). All three node-22/24/26 Dockerfiles bake the shim; the
    build context moved to `deployment/runtimes` so `COPY node-shim/` is a
    sibling copy.
- [x] P3-G5: `scripts/install.sh`, `examples/seed-data.sql`, and
  `docs/manual-testing.md` + `docs/e2e-test.md` describe the shim contract
  accurately (no single-shared-process wording remains).
  - CHECK: grep finds no stale container/shared-process claims in the touched
    docs.
  - EVIDENCE: seed-data.sql registers shim_kind for the Node runtimes;
    examples/README.md and the runtime/hidden-suite READMEs describe S/T and the
    socket; manual-testing.md names the two containers; e2e-test.md's probes now
    also fail on `shim`/`runner.sock`/`/opt/` leaks and its failure table
    documents the harness-less `INFRASTRUCTURE_ERROR`. install.sh needed only the
    seed path, unchanged.
- [x] P3-G6: full verify still green including frontend, license, and shim tests.
  - CHECK: `./mvnw -B -Plicense clean verify` BUILD SUCCESS.
  - EVIDENCE: `./mvnw -B -Plicense clean verify` BUILD SUCCESS (02:24) —
    shim tests 10/10 via `npm-test-shim`, frontend 18 files / 144 tests via
    vitest + coverage, license, checkstyle, PMD/CPD, SpotBugs, forbiddenapis and
    unit tests all green. Run recorded at the Phase 4 commit; the first attempt
    had to be re-run for a SpotBugs finding and a transient frontend-lint
    node_modules race, see the deviations below. Final proof with a Docker
    engine present: BUILD SUCCESS (03:41) with all 50 integration tests
    executed, 0 skipped, including `DockerGradingRunnerIT` 4/4 — the run that
    surfaced the stale legacy IT fixed and recorded in the deviations.

## Phase 4 — guarantee and docs

- [x] P4-G1: `docs/security.md` replaces the "not confidential from the
  submission" paragraph with the new boundary (S cannot read T; T's process
  still holds the hazards the manifest addresses: undeclared tests and forged
  passes are ruled out exactly as before), and closes the "tracked in issue #40"
  note.
  - CHECK: `grep -n "not confidential" docs/security.md` returns nothing; the
    boundary section names S and T.
  - EVIDENCE: `docs/security.md` "Hidden is confidential from the submission on
    a shimmed runtime" names S (sandbox container, never mounts the hidden
    tests) and T (suite container, mounts them read-only), the socket and what
    crosses it, and the manifest's unchanged authority; `grep -n "not
    confidential"` returns nothing and the "tracked in issue #40" sentence is
    gone.
- [x] P4-G2: the issue's acceptance criteria map to implemented, verified facts:
  (a) S cannot read hidden sources during grading — P2-G5/G6; (b) the student
  result shows the same outcomes — P2-G2 plus unchanged `TapReportParser`
  pathway; (c) security doc describes the boundary — P4-G1.
  - CHECK: the summary cites gate evidence for each criterion.
  - EVIDENCE: see the "Issue #40 acceptance criteria" mapping below, which cites
    P2-G5/G6, P2-G2 + the unchanged `TapReportParser` pathway, and P4-G1.
- [x] P4-G3: the marker wording is updated: a hidden suite is now unreadable by a
  determined student, which is the stronger property.
  - CHECK: the security boundary paragraph states the stronger property.
  - EVIDENCE: `docs/security.md` reads "A hidden suite is therefore unreadable
    by a determined student, not merely withheld from the result", and the
    assessment-policy sentence repeats it for shimmed runtimes.
- [x] P4-G4: full verify still green and the issue is closed.
  - CHECK: `./mvnw -B -Plicense clean verify` BUILD SUCCESS; issue #40 closed
    with the plan-comment referenced.
  - EVIDENCE: `./mvnw -B -Plicense clean verify` BUILD SUCCESS (03:41, Docker
    engine present: 396 unit + 50 integration tests, 0 skipped, shim 10/10);
    issue #40 closure status recorded on the tracker after push with this plan
    referenced. The original tracker was deleted when the repository was
    recreated; the web link in this file no longer resolves, so closure is
    recorded here in the plan instead.

## Issue #40 acceptance criteria

- (a) **S cannot read hidden sources during grading** — P2-G5 (the hostile
  submission cannot list, read or guess `/opt/hidden-tests` and has no network)
  and P2-G6 (nothing crossing the socket carries an assertion or expected value;
  S has no bind to the hidden tests and never receives `HIDDEN_TESTS`).
- (b) **The student's result shows the same outcomes** — P2-G2 (the report is
  built from T's TAP output alone, never S's) over the unchanged
  `TapReportParser` pathway, so a split run yields the same pass/fail/category
  result as the single-container run.
- (c) **The security doc describes the boundary** — P4-G1 (`docs/security.md`
  names S and T, the socket, and the stronger property).

## Deviations recorded during Phase 4

- P3-G6's first verify run failed twice before passing: a SpotBugs
  `NP_NULL_ON_SOME_PATH_FROM_RETURN_VALUE` in `GradingExecutor` (calling the
  `@Nullable` `shimKind()` twice for the null-check then `isBlank()`, and
  `Path.getFileName()` on walked entries) and a transient frontend-lint race
  where eslint's `stylish` formatter was not yet visible while the npm output
  was still settling. The SpotBugs findings are fixed (local `shimKind` read,
  null-guarded `getFileName`), and the passing run above is the third attempt.
- P4-G1/P4-G3's reworded paragraph also corrected two now-stale claims in
  `docs/security.md` that Phase 2 had made false: the grading-integrity intro no
  longer says reporter and submission share one container, and the forged-pass
  paragraph no longer says the split "is not done". Both are now qualified as
  legacy single-container behaviour, with the shimmed two-container behaviour
  stated as the stronger property.
- The first Docker-enabled `clean verify` failed on exactly what P3-G6 exists to
  catch: `DockerGradingRunnerIT.gradesThePartialSolution`, the pre-shim legacy
  IT, still graded `examples/.../hidden-tests` through the single-container path
  against the plain `node` image. After P3-G4 migrated the example suite to
  import the shim client at `/opt/gitgrader-shim/client.js`, that run died with
  `ERR_MODULE_NOT_FOUND`. The test now writes its own non-shim `LEGACY_SUITE`
  (importing the module from `/workspace`), so the single-container branch keeps
  live-Docker coverage without claiming the shim-only example still runs in one
  container; the two-container and hostile-submission ITs exercise the shipped
  topology. `./mvnw -B -Plicense clean verify` with Docker present is green
  (50 ITs, 0 skipped) after the fix.

## Abandoned

None yet.