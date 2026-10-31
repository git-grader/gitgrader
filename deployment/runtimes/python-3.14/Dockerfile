# Grading runs this image twice in a shimmed round: the sandbox (S) container
# holds the student repository at /workspace and starts the shim server, while
# the suite (T) container mounts the operator-owned hidden tests read-only at
# /opt/hidden-tests and reaches the submission over the shim socket. The shared
# Python shim is baked at /opt/gitgrader-shim, outside /workspace; never mount
# the test suite inside the student tree.
FROM python:3.15-slim-bookworm@sha256:4e03260adeeebafccd084f3584e6b84823aba5651cfb53ef8a078ed441ddf9cd

# pytest and pytest-tap are baked into the runtime so read-only operator
# test-suite mounts can execute pytest suites without installing dependencies
# at grading time. pytest-tap 3.5 loads --tap-stream; pipping a newer pytest
# together with it hits a duplicate-option conflict, so the two are pinned
# together here.
RUN pip install --no-cache-dir pytest==8.4.2 pytest-tap==3.5 tap.py==3.2.1

RUN mkdir /workspace && chown 65534:65534 /workspace

COPY python-shim/ /opt/gitgrader-shim/

WORKDIR /workspace
USER 65534:65534

# No ENTRYPOINT: the grading runner always supplies the command itself
# (`sh -c "<installCommand && testCommand>"`), so a fixed entrypoint would wrap
# it and break the run. The CMD below is only the default for `docker run`.
CMD ["cd /opt/hidden-tests && python3 -m pytest -p no:cacheprovider --tap-stream -q"]
