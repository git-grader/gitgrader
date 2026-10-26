# Grading runs this image twice in a shimmed round: the sandbox (S) container
# holds the student repository at /workspace and starts the shim server, while
# the suite (T) container mounts the operator-owned hidden tests read-only at
# /opt/hidden-tests and reaches the submission over the shim socket. The shared
# Python shim is baked at /opt/gitgrader-shim, outside /workspace; never mount
# the test suite inside the student tree.
FROM python@sha256:575f8bc12ec26542e0678bc6b60e385218248019d433157ec327264a78c3a853

# pytest is baked into the runtime so read-only operator test-suite mounts can
# execute tests without installing anything at grading time; the sandbox runs
# with no network. The version is pinned so a report parse never has to chase
# a pytest release.
RUN pip install --no-cache-dir --root-user-action=ignore "pytest==9.0.2"

RUN mkdir /workspace && chown 65534:65534 /workspace

COPY python-shim/ /opt/gitgrader-shim/

ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    PYTHONPATH=/opt/gitgrader-shim

WORKDIR /workspace
USER 65534:65534

# No ENTRYPOINT: the grading runner always supplies the command itself
# (`sh -c "<installCommand && testCommand>"`), so a fixed entrypoint would wrap
# it and break the run. The CMD below is only the default for `docker run`;
# the test command lives on the runtime row.
# -q keeps the terminal quiet so only the TAP reporter's lines reach stdout.
CMD ["cd /opt/hidden-tests && python -m pytest -p tap_reporter --gitgrader-tap -p no:cacheprovider -q"]
