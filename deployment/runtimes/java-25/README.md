# Java 25 grading runtime

This image runs Java assignments compiled and executed on a Temurin 25 JDK. It
pins the official multi-architecture `eclipse-temurin:25-jdk-noble` OCI index
at `sha256:7247bd8d2aeec64efe908dc72396d88101417d24ce3642703728382d96afb965`.

The Python majors live under `python-*` and the Node majors under `node-*`.
`junit-platform-console-standalone` 6.1.3 (JUnit 5 / Jupiter, verified by
checksum) is baked into the image for both student-visible and operator hidden
suites; the hidden suite emits TAP through the console launcher's `--tap`
flag, described under Runner contract. The runtime bakes the shared Java shim
from `deployment/runtimes/java-shim/` into the image at `/opt/gitgrader-shim`.

## Resolve and build

```sh
docker buildx imagetools inspect eclipse-temurin:25-jdk-noble
docker pull eclipse-temurin:25-jdk-noble
docker inspect --format='{{index .RepoDigests 0}}' eclipse-temurin:25-jdk-noble
./deployment/runtimes/build-runtimes.sh
```

The build runs the Java shim's own self-test (`java ShimSelfTest.java`) so a
regression in the shim fails the image build rather than shipping. The build
context is `deployment/runtimes`, so the Dockerfile can copy the shared
`java-shim/` directory into the image.

## Runner contract

A shimmed round grades in two containers running this digest-pinned image: the
sandbox (S) container holds only the student repository at `/workspace` and
starts the shim server; the suite (T) container mounts the operator-owned
hidden tests read-only at `/opt/hidden-tests` and opens the shim socket. S runs
with no network, a read-only root filesystem, `tmpfs` at `/tmp`, dropped
capabilities, and PID/memory/CPU limits, and never sees the hidden sources.

S compiles the submitted `.java` source in-process and runs the shim server:

```sh
java /opt/gitgrader-shim/Server
```

By default no per-assignment configuration is needed: an explicit
`SOLUTION_PATH` wins, and otherwise the server compiles the single `.java`
file directly under `/workspace/src` and refuses to guess when the directory
holds several. `SHIM_SOCKET` is set to the socket both containers share.

T compiles the hidden JUnit suite against the baked console jar and runs it
with `--tap`:

```sh
cd /opt/hidden-tests && javac -cp /opt/junit-platform-console-standalone.jar -d /tmp/classes *.java
java -jar /opt/junit-platform-console-standalone.jar --class-path /tmp/classes:/opt/junit-platform-console-standalone.jar --details none --tap
```

T's suite uses `ShimClient` from `/opt/gitgrader-shim/ShimClient.class`.
A suite that never opens the socket cannot be graded in two containers, so a
shimmed runtime refuses it as an infrastructure error. The Java runtime
registers no `install_command`: the sandbox runs with no network and the
assignments are zero-dependency, with javac and the console launcher baked
into the image, so there is nothing to install at grading time. TAP is the
report format registered in the runtime record.

## Register in GitGrader

Use the YAML printed by `build-runtimes.sh` in the runtime registration
mechanism for your deployment, or update the runtime row in
`examples/seed-data.sql` with the same immutable SHA-256 value. Do not use a
moving tag or `latest`; the database requires a 64-character `sha256:` digest
and records the tag only as documentation.

> **Before a java-25 runtime can grade a submission, the `sync-image-pins`
> quality gate (Quality workflow, `scripts/sync-image-pins.sh --check`) will
> report an error until the row is present in `seed-data.sql`. Add it after
> pushing the built image to a registry and obtaining its digest.
