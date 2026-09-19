# Sandbox isolation is enforced by the runner: network disabled, read-only root
# filesystem, tmpfs /tmp, all unnecessary capabilities dropped, and strict PID,
# memory, and CPU limits. Mount the operator-owned test suite read-only at
# /opt/hidden-tests, outside /workspace; never mount it in the student tree.
FROM node:22-bookworm-slim@sha256:48e4b67d85f87bd551df43704e24d252f56cc5f8e9718841aace50f19948f0f9

RUN mkdir /workspace && chown 65534:65534 /workspace

WORKDIR /workspace
USER 65534:65534

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["npm ci --ignore-scripts && node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js"]