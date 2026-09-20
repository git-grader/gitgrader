# Node 24 grading runtime

This image runs zero-dependency Node ESM assignments on Node 24. It pins the
official multi-architecture `node:24-bookworm-slim` OCI index at
`sha256:2fe369e969550cde8e867afc3fe370b260140cab4a23d467074295b42163d553`.
This digest was resolved with `docker buildx imagetools inspect` on 2026-09-19.

Sibling runtimes cover the other supported majors: `node-22` and `node-26`.
All Node runtimes bake one version-agnostic shim from
`deployment/runtimes/node-shim/` into the image at `/opt/gitgrader-shim`.

## Resolve and build

```sh
docker buildx imagetools inspect node:24-bookworm-slim
docker pull node:24-bookworm-slim
docker inspect --format='{{index .RepoDigests 0}}' node:24-bookworm-slim
./deployment/runtimes/build-runtimes.sh
```

The first command lists the OCI index and per-platform manifests. If changing
the base tag, update both the Dockerfile and `examples/seed-data.sql` to the new
OCI index digest before building. The helper prints the resulting local image
digest and ready-to-paste registration YAML for every runtime. The build context
is `deployment/runtimes`, so the Dockerfile can copy the shared
`node-shim/` directory into the image.

## Runner contract

A shimmed round grades in two containers running this digest-pinned image: the
sandbox (S) container holds only the student repository at `/workspace` and
starts the shim server; the suite (T) container mounts the operator-owned
hidden tests read-only at `/opt/hidden-tests` and opens the shim socket. S runs
with no network, a read-only root filesystem, `tmpfs` at `/tmp`, dropped
capabilities, and PID/memory/CPU limits, and never sees the hidden sources.

S sets `SOLUTION_PATH=/workspace/src/string-utils.js` and `SHIM_SOCKET` to a
socket both containers share, then runs:

```sh
node /opt/gitgrader-shim/server.js
```

T runs the suite over the same socket:

```sh
node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js
```

T's suite imports `createShimClient` from `/opt/gitgrader-shim/client.js` and
awaits the sandbox's exports through it; a suite that never connects cannot be
graded in two containers, so a shimmed runtime refuses it as an infrastructure
error. The image keeps the single-process command (`npm ci --ignore-scripts &&
node --test ...`) for non-shimmed runs. TAP is the report format registered in
the runtime record.

## Register in GitGrader

Use the YAML printed by `build-runtimes.sh` in the runtime registration mechanism
for your deployment, or update the runtime row in `examples/seed-data.sql` with
the same immutable SHA-256 value. Do not use a moving tag or `latest`; the
database requires a 64-character `sha256:` digest and records the tag only as
documentation.