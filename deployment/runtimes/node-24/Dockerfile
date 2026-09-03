# Sandbox isolation is enforced by the runner: network disabled, read-only root
# filesystem, tmpfs /tmp, all unnecessary capabilities dropped, and strict PID,
# memory, and CPU limits. Mount the operator-owned test suite read-only at
# /opt/hidden-tests, outside /workspace; never mount it in the student tree.
FROM node:24-bookworm-slim@sha256:ba849c60be29959425b8734d57b8b4b7d56f98edd9504c9af091d5281095a71e

RUN mkdir /workspace && chown 65534:65534 /workspace

WORKDIR /workspace
USER 65534:65534

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["npm ci --ignore-scripts && node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js"]
