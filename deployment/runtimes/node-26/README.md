# Node 26 grading runtime

This image runs zero-dependency Node ESM assignments on Node 26. It pins the
official multi-architecture `node:26-bookworm-slim` OCI index at
`sha256:582460f614631b59b824ac6020533b9bf339c7fdf3a6d7db31abb6b4065f0212`.
This digest was resolved with `docker buildx imagetools inspect` on 2026-09-19.

Sibling runtimes cover the other supported majors: `node-22` and `node-24`.
All Node runtimes share one version-agnostic shim in
`deployment/runtimes/node-shim/`, which the runner bind-mounts into both
containers of a shimmed round.

## Resolve and build

```sh
docker buildx imagetools inspect node:26-bookworm-slim
docker pull node:26-bookworm-slim
docker inspect --format='{{index .RepoDigests 0}}' node:26-bookworm-slim
./deployment/runtimes/build-runtimes.sh
```

The first command lists the OCI index and per-platform manifests. If changing
the base tag, update both the Dockerfile and `examples/seed-data.sql` to the new
OCI index digest before building. The helper prints the resulting local image
digest and ready-to-paste registration YAML for every runtime.

## Runner contract

The runner copies the student repository to `/workspace`, mounts the
operator-owned suite read-only at `/opt/hidden-tests`, sets
`SOLUTION_PATH=/workspace/src/string-utils.js`, and starts the image with no
network, a read-only root filesystem, `tmpfs` at `/tmp`, dropped capabilities,
and PID/memory/CPU limits. The image runs:

```sh
npm ci --ignore-scripts && node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js
```

The template has no dependencies and includes a lockfile, so `npm ci` completes
offline. TAP is the report format registered in the runtime record.

## Register in GitGrader

Use the YAML printed by `build-runtimes.sh` in the runtime registration mechanism
for your deployment, or update the runtime row in `examples/seed-data.sql` with
the same immutable SHA-256 value. Do not use a moving tag or `latest`; the
database requires a 64-character `sha256:` digest and records the tag only as
documentation.