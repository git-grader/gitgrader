# Sandbox isolation is enforced by the runner: network disabled, read-only root
# filesystem, tmpfs /tmp, all unnecessary capabilities dropped, and strict PID,
# memory, and CPU limits. Mount the operator-owned test suite read-only at
# /opt/hidden-tests, outside /workspace; never mount it in the student tree.
FROM node:25-bookworm-slim@sha256:81db02c4b671288a03915da9534dbd54f96d0e7c24d80ccc54f5b36b2e684370

RUN mkdir /workspace && chown 65534:65534 /workspace

WORKDIR /workspace
USER 65534:65534

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["npm ci --ignore-scripts && node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js"]
