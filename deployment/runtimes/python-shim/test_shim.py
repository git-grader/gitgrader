# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""Tests for the Python grading shim, mirroring the Node shim's own suite.

No Docker is needed: a server and a client share a socket in a temporary
directory and talk over the real protocol.
"""

import math
import os
import sys
import tempfile

import pytest

# The shim modules are plain files in this directory; make them importable.
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from client import ShimClient, ShimError, create_shim_client  # noqa: E402
from phases import PHASES  # noqa: E402
from serializable import deserialize, serialize  # noqa: E402
from server import (  # noqa: E402
    DEFAULT_CALL_TIMEOUT_MS,
    DEFAULT_SOLUTION_PATH,
    ShimServer,
    ShimStartupError,
    create_shim_server,
    resolve_solution_path,
)

CONNECT_TIMEOUT_MS = 2_000


@pytest.fixture()
def socket_dir(tmp_path):
    return str(tmp_path / "sockets")


def write_solution(socket_dir, source, name="solution.py"):
    """Writes a solution module and returns its path."""
    import pathlib

    src_dir = pathlib.Path(socket_dir) / "etc" / "solution"
    src_dir.mkdir(parents=True, exist_ok=True)
    path = src_dir / name
    path.write_text(source)
    return str(path)


def pair(socket_dir, source):
    """Starts a shim server in front of a hand-written solution and hands the test the connected client.

    :param socket_dir: directory under which to create the socket and solution
    :param source: source of the solution module to serve
    :returns: (server, client) both bound to the same shared socket
    """
    solution_path = write_solution(socket_dir, source)
    socket_path = os.path.join(socket_dir, "runner.sock")

    def log(line):
        sys.stderr.write(line + "\n")

    server = create_shim_server(
        socket_path=socket_path, solution_path=solution_path, call_timeout_ms=CONNECT_TIMEOUT_MS, log=log
    )
    return server, create_shim_client(socket_path=socket_path, connect_timeout_ms=CONNECT_TIMEOUT_MS)


@pytest.fixture()
def pair_fixture(socket_dir):
    server, client = pair(
        socket_dir,
        """
def truncate(text, length):
    return text[:length]

def slugify(text):
    return text.lower().replace(" ", "-")

def never():
    import time
    time.sleep(60)
    return "too late"

def boom():
    raise ValueError("saw a ghost")

def unrepresentable():
    return float("nan")
""",
    )
    yield server, client
    client.close()
    server.close()


def test_round_trips_the_submitted_exports(pair_fixture):
    server, client = pair_fixture
    assert client.truncate("hello world", 5) == "hello"
    assert client.slugify("Hello World") == "hello-world"


def test_calls_may_share_the_socket_concurrently(pair_fixture):
    server, client = pair_fixture
    results = set()
    for _ in range(5):
        results.add(client.truncate("abcdefghij", 3))
    assert results == {"abc"}


def test_reports_a_missing_export(pair_fixture):
    server, client = pair_fixture
    with pytest.raises(ShimError) as excinfo:
        client.nonexistent(1)
    assert excinfo.value.phase == PHASES.NO_SUCH_MEMBER


def test_reports_a_thrown_exception_without_the_stack(pair_fixture):
    server, client = pair_fixture
    with pytest.raises(ShimError) as excinfo:
        client.boom()
    assert excinfo.value.phase == PHASES.INVOCATION_ERROR
    assert "saw a ghost" in str(excinfo.value)
    assert "server.py" not in str(excinfo.value)


def test_reports_a_hung_call_as_a_timeout(pair_fixture):
    server, client = pair_fixture
    with pytest.raises(ShimError) as excinfo:
        client.never()
    assert excinfo.value.phase == PHASES.INVOCATION_TIMEOUT


def test_reports_an_unrepresentable_result(pair_fixture):
    server, client = pair_fixture
    with pytest.raises(ShimError) as excinfo:
        client.unrepresentable()
    assert excinfo.value.phase == PHASES.NON_SERIALIZABLE_RESULT


def test_after_close_every_call_fails_as_closed(pair_fixture):
    server, client = pair_fixture
    client.close()
    with pytest.raises(ShimError) as excinfo:
        client.truncate("x", 1)
    assert excinfo.value.phase == PHASES.CONNECTION_CLOSED


def test_connect_retries_until_the_sandbox_listens(socket_dir):
    solution_path = write_solution(socket_dir, "def hello():\n    return 'hi'\n")
    socket_path = os.path.join(socket_dir, "runner.sock")
    server = create_shim_server(
        socket_path=socket_path, solution_path=solution_path, call_timeout_ms=CONNECT_TIMEOUT_MS, log=lambda *_: None
    )
    client = create_shim_client(socket_path=socket_path, connect_timeout_ms=CONNECT_TIMEOUT_MS)
    assert client.hello() == "hi"
    client.close()
    server.close()


def test_resolve_solution_path_prefers_the_explicit_path(socket_dir):
    solution = write_solution(socket_dir, "def a():\n    pass\n")
    assert resolve_solution_path(explicit=solution, fallback=DEFAULT_SOLUTION_PATH) == solution


def test_resolve_solution_path_finds_the_single_module(socket_dir):
    # A single .py file in the fallback directory is returned even when that exact
    # path does not yet exist; discovery picks it up from the directory scan.
    import pathlib, os

    src_dir = pathlib.Path(socket_dir) / "src"
    src_dir.mkdir(parents=True)
    (src_dir / "solution.py").write_text("def a():\n    pass\n")

    # Pretend the conventional path does not exist yet, so discovery kicks in.
    fallback = str(src_dir / "solution.py")
    original_exists = os.path.exists

    def patched_exists(path):
        if path == fallback:
            return False  # simulate the file not being created yet
        return original_exists(path)

    os.path.exists = patched_exists
    try:
        result = resolve_solution_path(explicit=None, fallback=fallback)
        assert result == str(src_dir / "solution.py")
    finally:
        os.path.exists = original_exists


def test_resolve_solution_path_refuses_to_guess_between_many(socket_dir):
    write_solution(socket_dir, "def a():\n    pass\n", name="one.py")
    write_solution(socket_dir, "def b():\n    pass\n", name="two.py")
    with pytest.raises(ShimStartupError):
        resolve_solution_path(fallback="/workspace/src/solution.py")


def test_serialize_round_trips_plain_values():
    value = {"text": "ok", "n": 3, "flag": True, "nothing": None, "items": [1, 2.5, "x"]}
    assert deserialize(serialize(value, "v")) == value


def test_serialize_marks_tuples_and_bytes():
    wire = serialize((1, b"ab"), "v")
    assert deserialize(wire) == (1, b"ab")


def test_serialize_refuses_values_json_cannot_carry():
    for bad in (float("nan"), float("inf"), {1, 2}, 3 + 4j):
        with pytest.raises(TypeError):
            serialize(bad, "v")


def test_serialize_refuses_circular_structures():
    value = []
    value.append(value)
    with pytest.raises(TypeError):
        serialize(value, "v")


def test_serialize_refuses_non_string_dict_keys():
    with pytest.raises(TypeError):
        serialize({1: "one"}, "v")
