# Sandbox isolation is enforced by the runner: network disabled, read-only root
# filesystem, tmpfs /tmp, all unnecessary capabilities dropped, and strict PID,
# memory, and CPU limits. Mount the operator-owned test suite read-only at
# /opt/hidden-tests, outside /workspace; never mount it in the student tree.
FROM node:26-bookworm-slim@sha256:cd565714d4da3e84bfd341e31448f81d47c6362198f152345297c9c1154e6341

RUN mkdir /workspace && chown 65534:65534 /workspace

WORKDIR /workspace
USER 65534:65534

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["npm ci --ignore-scripts && node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js"]
