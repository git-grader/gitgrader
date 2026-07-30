# Node 24 grading runtime

This image runs zero-dependency Node ESM assignments. It pins the official
multi-architecture `node:24-bookworm-slim` OCI index at
`sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d`.
This digest was resolved with `docker buildx imagetools inspect` on 2026-07-29.

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
digest and ready-to-paste registration YAML.

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
