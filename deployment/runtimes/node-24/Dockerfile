# Sandbox isolation is enforced by the runner: network disabled, read-only root
# filesystem, tmpfs /tmp, all unnecessary capabilities dropped, and strict PID,
# memory, and CPU limits. Mount the operator-owned test suite read-only at
# /opt/hidden-tests, outside /workspace; never mount it in the student tree.
FROM node:24-bookworm-slim@sha256:2fe369e969550cde8e867afc3fe370b260140cab4a23d467074295b42163d553

RUN mkdir /workspace && chown 65534:65534 /workspace

WORKDIR /workspace
USER 65534:65534

ENTRYPOINT ["/bin/sh", "-c"]
CMD ["npm ci --ignore-scripts && node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js"]
