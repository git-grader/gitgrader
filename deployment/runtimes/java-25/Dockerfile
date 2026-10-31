# Grading runs this image twice in a shimmed round: the sandbox (S) container
# holds the student repository at /workspace and starts the shim server, while
# the suite (T) container mounts the operator-owned hidden tests read-only at
# /opt/hidden-tests and reaches the submission over the shim socket. The shared
# Java shim is baked at /opt/gitgrader-shim, outside /workspace; never mount the
# test suite inside the student tree.
FROM eclipse-temurin:25-jdk-noble@sha256:7247bd8d2aeec64efe908dc72396d88101417d24ce3642703728382d96afb965

# The sandbox compiles the submitted source in-process, so a JDK (javac) not a
# JRE must be the base image. Junit platform console standalone is baked in so
# read-only operator test-suite mounts can compile and run JUnit suites without
# installing anything at grading time; its own SBA checksums pin the jar.
RUN apt-get update \
 && apt-get install -y --no-install-recommends curl ca-certificates \
 && curl -fsSL -o /opt/junit-platform-console-standalone.jar \
      https://repo1.maven.org/maven2/org/junit/platform/junit-platform-console-standalone/6.1.3/junit-platform-console-standalone-6.1.3.jar \
 && printf '%s  %s\n' 'bb65bef57072d36433e8a863016163858c926113' /opt/junit-platform-console-standalone.jar | sha1sum -c - \
 && rm -rf /var/lib/apt/lists/* /root/.cache

# The Java shim is compiled into the image from a bare JDK. The JUnit-driven
# TapRunner is compiled against the baked console jar. Running the shim's own
# self-test at build time is what keeps a regression from shipping in a baked
# runtime layer.
COPY java-shim/ /opt/gitgrader-shim-src/
WORKDIR /opt/gitgrader-shim-src
RUN javac -Xlint:all -d /opt/gitgrader-shim Phases.java Serializable.java Json.java ShimClient.java Server.java ShimSelfTest.java \
 && javac -cp /opt/junit-platform-console-standalone.jar -d /opt/gitgrader-shim TapRunner.java \
 && java -cp /opt/gitgrader-shim ShimSelfTest

RUN mkdir /workspace && chown 65534:65534 /workspace

WORKDIR /workspace
USER 65534:65534

# No ENTRYPOINT: the grading runner always supplies the command itself
# (`sh -c "<installCommand && testCommand>"`), so a fixed entrypoint would wrap
# it and break the run. The CMD below is only the default for `docker run`:
# compile the mounted hidden tests to /tmp/classes (the suite's writable tmpfs),
# against the JUnit API and the baked shim, then run them through TapRunner,
# which emits the TAP report the runner parses.
CMD ["cd /opt/hidden-tests && javac -cp /opt/junit-platform-console-standalone.jar:/opt/gitgrader-shim -d /tmp/classes *.java && java -cp /tmp/classes:/opt/gitgrader-shim:/opt/junit-platform-console-standalone.jar TapRunner"]
