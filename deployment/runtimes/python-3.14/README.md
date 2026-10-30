# Python grading runtime

This image runs zero-dependency Python assignments on Python 3.14. It pins the
official multi-architecture `python:3.14-slim-bookworm` OCI index at
`sha256:516b51cb9db2ef061e465c803e50f64361b82cefd91e525b889b967b83e83527`.

JVM assignments are covered by the sibling `java-25` runtime; the Node majors
live in `node-22`, `node-24` and `node-26`. `pytest` 8.4.2 with `pytest-tap`
3.5 is installed globally in the image for both student-visible and operator
hidden suites; the hidden suite emits TAP through pytest's `--tap-stream`
flag, described under Runner contract. The runtime bakes the shared Python
shim from `deployment/runtimes/python-shim/` into the image at
`/opt/gitgrader-shim`.

## Resolve and build

```sh
docker buildx imagetools inspect python:3.14-slim-bookworm
docker pull python:3.14-slim-bookworm
docker inspect --format='{{index .RepoDigests 0}}' python:3.14-slim-bookworm
./deployment/runtimes/build-runtimes.sh
```

The first command lists the OCI index and per-platform manifests. If changing
the base tag, update both the Dockerfile and `examples/seed-data.sql` to the
new OCI index digest before building. The helper prints the resulting local
image digest and ready-to-paste registration YAML for every runtime. The build
context is `deployment/runtimes`, so the Dockerfile can copy the shared
`python-shim/` directory into the image.

## Runner contract

A shimmed round grades in two containers running this digest-pinned image: the
sandbox (S) container holds only the student repository at `/workspace` and
starts the shim server; the suite (T) container mounts the operator-owned
hidden tests read-only at `/opt/hidden-tests` and opens the shim socket. S runs
with no network, a read-only root filesystem, `tmpfs` at `/tmp`, dropped
capabilities, and PID/memory/CPU limits, and never sees the hidden sources.

S picks the submitted module, then runs:

```sh
python3 /opt/gitgrader-shim/server.py
```

By default no per-assignment configuration is needed: an explicit
`SOLUTION_PATH` wins, and otherwise the server imports the single `.py` file
directly under `/workspace/src` and refuses to guess when the directory holds
several. `SHIM_SOCKET` is set to the socket both containers share.

T runs the hidden pytest suite over the same socket, and `--tap-stream` makes
pytest emit TAP on its standard output:

```sh
cd /opt/hidden-tests && python3 -m pytest -p no:cacheprovider --tap-stream -q
```

T's suite imports `create_shim_client` from `/opt/gitgrader-shim/client.py`
and awaits the sandbox's exports through it; a suite that never connects
cannot be graded in two containers, so a shimmed runtime refuses it as an
infrastructure error. The Python runtime registers no `install_command`: the
sandbox runs with no network and the assignments are zero-dependency, with
pytest and pytest-tap baked into the image, so there is nothing to install at
grading time. TAP is the report format registered in the runtime record.

## Register in GitGrader

Use the YAML printed by `build-runtimes.sh` in the runtime registration
mechanism for your deployment, or update the runtime row in
`examples/seed-data.sql` with the same immutable SHA-256 value. Do not use a
moving tag or `latest`; the database requires a 64-character `sha256:` digest
and records the tag only as documentation.
