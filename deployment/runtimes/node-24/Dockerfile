# Grading runs this image twice in a shimmed round: the sandbox (S) container
# holds the student repository at /workspace and starts the shim server, while
# the suite (T) container mounts the operator-owned hidden tests read-only at
# /opt/hidden-tests and reaches the submission over the shim socket. The shared
# Node shim is baked at /opt/gitgrader-shim, outside /workspace; never mount the
# test suite inside the student tree.
FROM node:24-bookworm-slim@sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205

# Jasmine is baked into the runtime so read-only operator test-suite mounts can
# execute Jasmine specs without installing dependencies at grading time.
RUN npm install --global --ignore-scripts jasmine@7.0.0

RUN mkdir /workspace && chown 65534:65534 /workspace

COPY node-shim/ /opt/gitgrader-shim/

WORKDIR /workspace
USER 65534:65534

# No ENTRYPOINT: the grading runner always supplies the command itself
# (`sh -c "<installCommand && testCommand>"`), so a fixed entrypoint would wrap
# it and break the run. The CMD below is only the default for `docker run`.
CMD ["cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs"]
