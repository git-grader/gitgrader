# Grading runs this image twice in a shimmed round: the sandbox (S) container
# holds the student repository at /workspace and starts the shim server, while
# the suite (T) container mounts the operator-owned hidden tests read-only at
# /opt/hidden-tests and reaches the submission over the shim socket. The shared
# Python shim is baked at /opt/gitgrader-shim, outside /workspace; never mount
# the test suite inside the student tree.
FROM python:3.15-slim-bookworm@sha256:96599df822d0e1c6bb700014a05c7f3c8cabc6e0f7ff3c4f9f69cee033b4b5d9

# pytest is baked into the runtime so read-only operator test-suite mounts can
# execute pytest suites without installing dependencies at grading time. Hidden
# suites are run through /opt/gitgrader-shim/tap_runner.py (copied in below),
# which drives pytest in-process and prints the TAP the grading parser reads;
# pytest-tap's --tap-stream is not used because it omits the "- " separator.
RUN pip install --no-cache-dir pytest==8.4.2

RUN mkdir /workspace && chown 65534:65534 /workspace

COPY python-shim/ /opt/gitgrader-shim/

WORKDIR /workspace
USER 65534:65534

# No ENTRYPOINT: the grading runner always supplies the command itself
# (`sh -c "<installCommand && testCommand>"`), so a fixed entrypoint would wrap
# it and break the run. The CMD below is only the default for `docker run`.
CMD ["cd /opt/hidden-tests && python3 /opt/gitgrader-shim/tap_runner.py"]
