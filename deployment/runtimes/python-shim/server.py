# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""
Starts a shim server in front of the submitted solution.

The server loads the module named by ``solutionPath`` and serves each of its
exported functions to the suite side over a Unix socket: one NDJSON request
per call, one NDJSON response per request, correlated by id. Values are
carried in both directions through :mod:`serializable`. A call may finish in
whatever time it takes up to ``call_timeout_ms``; a call that exceeds it
fails with ``invocation-timeout`` so the suite is not stuck on a solution
that never returns.
"""

import importlib.util
import json
import os
import socket
import sys
import threading

from phases import (
    BAD_REQUEST,
    INVOCATION_ERROR,
    INVOCATION_TIMEOUT,
    NON_SERIALIZABLE_ARGS,
    NON_SERIALIZABLE_RESULT,
    NO_SUCH_MEMBER,
    NOT_CALLABLE,
)
from serializable import NonSerializable, deserialize, serialize

DEFAULT_SOCKET = "/gitgrader-shim/runner.sock"
DEFAULT_SOLUTION_PATH = "/workspace/src/solution.py"
DEFAULT_CALL_TIMEOUT_MS = 30_000


class ShimStartupError(Exception):
    """Raised before the server listens, e.g. the module could not load."""


def discover_solution_modules(src_dir):
    """List the submitted modules a workspace offers in its src/ directory.

    :param src_dir: absolute path of the src/ directory to scan
    :returns: absolute paths of the .py files directly under it, sorted,
        or ``[]`` when the directory is absent
    """
    if not os.path.isdir(src_dir):
        return []
    entries = sorted(
        os.path.join(src_dir, name)
        for name in os.listdir(src_dir)
        if name.endswith(".py") and os.path.isfile(os.path.join(src_dir, name))
    )
    # A module's own __init__ is never the submission; Python packages a
    # directory of files, and the single-module rule below still applies.
    return entries


def resolve_solution_path(explicit, fallback=DEFAULT_SOLUTION_PATH):
    """Resolve which submitted module the server should load.

    An explicit ``SOLUTION_PATH`` wins when the named file exists. Otherwise
    the server looks for the single module in the workspace's src/ directory,
    so assignments work without a per-assignment path wired into the grader.
    When src/ holds several modules the server refuses to guess and asks for
    an explicit SOLUTION_PATH.
    """
    if explicit and os.path.isfile(explicit):
        return explicit
    src_dir = os.path.dirname(explicit or fallback)
    modules = discover_solution_modules(src_dir)
    if len(modules) == 1:
        return modules[0]
    if not modules:
        raise ShimStartupError(
            f"Could not find the submitted solution: nothing named {fallback} "
            f"exists and {src_dir} holds no module."
        )
    raise ShimStartupError(
        f"Could not choose the submitted solution: {src_dir} holds "
        f"{len(modules)} modules ({', '.join(modules)}). Set SOLUTION_PATH to the one to test."
    )


def load_solution_module(solution_path):
    """Import the submitted module from its file path."""
    spec = importlib.util.spec_from_file_location("gitgrader_solution", solution_path)
    if spec is None or spec.loader is None:
        raise ShimStartupError(f"Could not load the submitted solution from {solution_path}")
    module = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(module)
    except Exception as error:
        raise ShimStartupError(
            f"Could not load the submitted solution from {solution_path}: {error}"
        ) from error
    return module


def create_shim_server(
    socket_path=DEFAULT_SOCKET,
    solution_path=DEFAULT_SOLUTION_PATH,
    call_timeout_ms=DEFAULT_CALL_TIMEOUT_MS,
    log=None,
):
    """Start a shim server in front of the submitted solution.

    The server loads the module named by ``solution_path`` and serves each of
    its exported callables to the suite side over a Unix socket: one NDJSON
    request per call, one NDJSON response per request, correlated by id. A
    call may finish in whatever time it takes up to ``call_timeout_ms``; a
    call that exceeds it fails with ``invocation-timeout`` so the suite is
    not stuck on a solution that never returns.

    :returns: a handle with a ``close`` function
    """
    if log is None:
        def log(_line):
            pass

    directory = os.path.dirname(socket_path)
    if directory:
        os.makedirs(directory, exist_ok=True)
    try:
        os.unlink(socket_path)
    except FileNotFoundError:
        pass

    module = load_solution_module(solution_path)

    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(socket_path)
    os.chmod(socket_path, 0o666)
    server.listen(16)
    log(f"shim server listening on {socket_path} (solution {solution_path})")

    stop = threading.Event()

    def accept_loop():
        server.settimeout(0.5)
        while not stop.is_set():
            try:
                conn, _ = server.accept()
            except socket.timeout:
                continue
            except OSError:
                break
            threading.Thread(target=serve_connection, args=(conn, module, call_timeout_ms), daemon=True).start()

    listener = threading.Thread(target=accept_loop, daemon=True)
    listener.start()

    def close():
        stop.set()
        try:
            server.close()
        except OSError:
            pass
        try:
            os.unlink(socket_path)
        except FileNotFoundError:
            pass

    return {"close": close, "thread": listener}


def serve_connection(conn, module, call_timeout_ms):
    buffer = b""
    with conn:
        while True:
            try:
                chunk = conn.recv(65536)
            except OSError:
                break
            if not chunk:
                break
            buffer += chunk
            while True:
                newline = buffer.find(b"\n")
                if newline == -1:
                    break
                line = buffer[:newline]
                buffer = buffer[newline + 1 :]
                if line.strip():
                    serve_line(conn, line, module, call_timeout_ms)


def serve_line(sock, line, module, call_timeout_ms):
    def write(message):
        try:
            sock.sendall((json.dumps(message) + "\n").encode("utf-8"))
        except OSError:
            pass

    try:
        request = json.loads(line)
    except ValueError:
        write({"error": {"phase": BAD_REQUEST, "message": "Malformed JSON on the shim socket"}})
        return
    if not isinstance(request, dict):
        write({"error": {"phase": BAD_REQUEST, "message": "Malformed JSON on the shim socket"}})
        return
    call_id = request.get("id")
    method = request.get("method")
    wire_args = request.get("args")
    if not isinstance(call_id, int) or not isinstance(method, str) or not isinstance(wire_args, list):
        write({
            "error": {
                "phase": BAD_REQUEST,
                "message": "A shim request needs a numeric id, a string method and an array of args",
            }
        })
        return

    try:
        args = [deserialize(arg) for arg in wire_args]
    except TypeError as error:
        write({"id": call_id, "error": {"phase": NON_SERIALIZABLE_ARGS, "message": str(error)}})
        return

    member = getattr(module, method, _MISSING)
    if member is _MISSING:
        write({
            "id": call_id,
            "error": {"phase": NO_SUCH_MEMBER, "message": f"The submitted solution does not export '{method}'"},
        })
        return
    if not callable(member):
        write({"id": call_id, "error": {"phase": NOT_CALLABLE, "message": f"'{method}' is not callable by the suite"}})
        return

    try:
        result = _with_timeout(lambda: member(*args), call_timeout_ms)
    except _ShimTimeout:
        write({
            "id": call_id,
            "error": {
                "phase": INVOCATION_TIMEOUT,
                "message": f"'{method}' exceeded the {call_timeout_ms}ms per-call shim limit",
            },
        })
        return
    except Exception as error:
        write({"id": call_id, "error": {"phase": INVOCATION_ERROR, "message": _describe_thrown(error)}})
        return

    try:
        wire_result = serialize(result, "shim result")
    except NonSerializable as error:
        write({"id": call_id, "error": {"phase": NON_SERIALIZABLE_RESULT, "message": str(error)}})
        return
    write({"id": call_id, "result": wire_result})


class _Missing:
    """Sentinel for an export the solution does not have."""


_MISSING = _Missing()


class _ShimTimeout(Exception):
    """Marks that the per-call ceiling passed, for the timeout phase."""


def _with_timeout(fn, timeout_ms):
    import concurrent.futures

    with concurrent.futures.ThreadPoolExecutor(max_workers=1) as pool:
        future = pool.submit(fn)
        try:
            return future.result(timeout=timeout_ms / 1000)
        except concurrent.futures.TimeoutError:
            raise _ShimTimeout() from None


def _describe_thrown(thrown):
    """Turn whatever a solution raised into the message that reaches the suite.

    The stack is deliberately not shipped: it names paths and line numbers the
    student must not learn from a hidden call.
    """
    message = str(thrown)
    return message if message else thrown.__class__.__name__


def main():
    socket_path = os.environ.get("SHIM_SOCKET", DEFAULT_SOCKET)
    explicit = os.environ.get("SOLUTION_PATH")
    try:
        solution_path = resolve_solution_path(explicit, DEFAULT_SOLUTION_PATH)
    except ShimStartupError as error:
        sys.stderr.write(f"The grading shim server could not start: {error}\n")
        raise SystemExit(1) from error
    timeout_ms = int(os.environ.get("SHIM_CALL_TIMEOUT_MS", DEFAULT_CALL_TIMEOUT_MS))

    def log(line):
        sys.stderr.write(line + "\n")
        sys.stderr.flush()

    try:
        server = create_shim_server(
            socket_path=socket_path,
            solution_path=solution_path,
            call_timeout_ms=timeout_ms,
            log=log,
        )
    except ShimStartupError as error:
        sys.stderr.write(f"The grading shim server could not start: {error}\n")
        raise SystemExit(1) from error
    try:
        threading.Event().wait()
    except KeyboardInterrupt:
        pass
    finally:
        server["close"]()


if __name__ == "__main__":
    main()
