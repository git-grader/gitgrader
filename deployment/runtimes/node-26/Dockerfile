# Grading runs this image twice in a shimmed round: the sandbox (S) container
# holds the student repository at /workspace and starts the shim server, while
# the suite (T) container mounts the operator-owned hidden tests read-only at
# /opt/hidden-tests and reaches the submission over the shim socket. The shared
# Node shim is baked at /opt/gitgrader-shim, outside /workspace; never mount the
# test suite inside the student tree.
FROM node:26-bookworm-slim@sha256:582460f614631b59b824ac6020533b9bf339c7fdf3a6d7db31abb6b4065f0212

# Jasmine is baked into the runtime so read-only operator test-suite mounts can
# execute Jasmine specs without installing dependencies at grading time.
RUN npm install --global --ignore-scripts jasmine@6.3.0

RUN mkdir /workspace && chown 65534:65534 /workspace

COPY node-shim/ /opt/gitgrader-shim/

WORKDIR /workspace
USER 65534:65534

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs"]
