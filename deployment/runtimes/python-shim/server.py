# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""Unix-socket shim server that exposes a Python solution's callable exports.

Each request is answered exactly once, possibly out of the order it arrived,
because calls run on their own threads and may take up to the per-call ceiling.
The suite side correlates responses by id, never by stream order.
"""

import importlib.util
import json
import os
import signal
import socket
import threading

from phases import PHASES
from serializable import deserialize, serialize

DEFAULT_SOCKET = "/gitgrader-shim/runner.sock"
DEFAULT_SOLUTION_PATH = "/workspace/src/solution.py"
DEFAULT_CALL_TIMEOUT_MS = 30_000


def resolve_solution_path(explicit=None, fallback=DEFAULT_SOLUTION_PATH):
    """Finds the submitted module to serve.

    :param explicit: value of the SOLUTION_PATH environment variable, or None
    :param fallback: the runtime's conventional path when nothing is set
    :returns: absolute path of the module to load
    :raises ShimStartupError: when no unique source module can be chosen
    """
    chosen = explicit or fallback
    if chosen and os.path.exists(chosen):
        return chosen
    src_dir = os.path.dirname(fallback)
    modules = _discover_solution_modules(src_dir)
    if len(modules) == 1:
        return modules[0]
    if not modules:
        raise ShimStartupError(
            f"Could not find the submitted solution: nothing named {fallback} "
            f"exists and {src_dir} holds no .py file."
        )
    raise ShimStartupError(
        f"Could not choose the submitted solution: {src_dir} holds "
        f"{len(modules)} modules ({', '.join(modules)}). "
        "Set SOLUTION_PATH to the one to test."
    )


def _discover_solution_modules(src_dir):
    if not os.path.isdir(src_dir):
        return []
    return sorted(entry.path for entry in os.scandir(src_dir) if entry.is_file() and entry.name.endswith(".py"))


class ShimStartupError(Exception):
    """Raised before the server starts, e.g. when the submitted module cannot be loaded."""


class ShimServer:
    """NDJSON-over-Unix-socket server serving a solution's callable exports."""

    def __init__(self, socket_path, namespace, call_timeout_ms, log):
        self._socket_path = socket_path
        self._namespace = namespace
        self._call_timeout_ms = call_timeout_ms
        self._log = log
        self._server_sock = None
        self._stopping = False
        self._connections = []
        self._connections_lock = threading.Lock()

    def start(self):
        os.makedirs(os.path.dirname(self._socket_path), exist_ok=True)
        try:
            os.unlink(self._socket_path)
        except FileNotFoundError:
            pass
        self._server_sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        self._server_sock.bind(self._socket_path)
        self._server_sock.listen(16)
        self._log(f"shim server listening on {self._socket_path}")
        self._accept_thread = threading.Thread(target=self._accept_loop, daemon=True, name="shim-accept")
        self._accept_thread.start()

    def _accept_loop(self):
        while not self._stopping:
            try:
                conn, _ = self._server_sock.accept()
            except OSError:
                if self._stopping:
                    break
                raise
            with self._connections_lock:
                self._connections.append(conn)
            threading.Thread(
                target=_handle_connection,
                args=(conn, self),
                daemon=True,
                name="shim-conn",
            ).start()

    def close(self):
        self._stopping = True
        if self._server_sock:
            try:
                self._server_sock.close()
            except OSError:
                pass
        with self._connections_lock:
            for conn in self._connections:
                try:
                    conn.shutdown(socket.SHUT_RDWR)
                    conn.close()
                except OSError:
                    pass
            self._connections.clear()
        try:
            os.unlink(self._socket_path)
        except FileNotFoundError:
            pass


def create_shim_server(
    socket_path=DEFAULT_SOCKET,
    solution_path=DEFAULT_SOLUTION_PATH,
    call_timeout_ms=DEFAULT_CALL_TIMEOUT_MS,
    log=lambda *_: None,
):
    """Starts a shim server in front of the submitted solution.

    :param socket_path: where to listen; must be on the volume shared with the suite side
    :param solution_path: the submitted module to load
    :param call_timeout_ms: ceiling on one call in milliseconds
    :param log: diagnostic sink (the runner reads stderr, never this as a report)
    :returns: :class:`ShimServer` handle whose :meth:`close` shuts the server down
    :raises ShimStartupError: when the submitted module cannot be loaded
    """
    namespace = _load_solution(solution_path)
    server = ShimServer(socket_path, namespace, call_timeout_ms, log)
    server.start()
    return server


def _load_solution(solution_path):
    spec = importlib.util.spec_from_file_location("_solution", solution_path)
    if spec is None or spec.loader is None:
        raise ShimStartupError(
            f"Could not load the submitted solution from {solution_path}: not a recognised Python module"
        )
    module = importlib.util.module_from_spec(spec)
    try:
        spec.loader.exec_module(module)
    except BaseException as error:  # noqa: BLE001 — a student may raise anything
        raise ShimStartupError(f"Could not load the submitted solution from {solution_path}: {error}") from error
    # Exports are the module-level callables whose names do not start with '_'.
    return {name: value for name, value in vars(module).items() if not name.startswith("_") and callable(value)}


def _handle_connection(conn, server):
    write_lock = threading.Lock()
    try:
        buf = bytearray()
        while True:
            try:
                chunk = conn.recv(4096)
            except OSError:
                break
            if not chunk:
                break
            buf.extend(chunk)
            while True:
                newline = buf.find(b"\n")
                if newline == -1:
                    break
                line_bytes = bytes(buf[:newline])
                del buf[: newline + 1]
                if not line_bytes.strip():
                    continue
                line = line_bytes.decode("utf-8", errors="replace")
                _serve_request(line, conn, write_lock, server)
    except OSError:
        pass
    finally:
        with server._connections_lock:
            if conn in server._connections:
                server._connections.remove(conn)
        try:
            conn.close()
        except OSError:
            pass


def _send(write_lock, conn, message):
    try:
        line = json.dumps(message)
        with write_lock:
            conn.sendall((line + "\n").encode("utf-8", errors="replace"))
    except OSError:
        # The suite side went away mid-response; the connection teardown that
        # follows is the runner's job, not the server's.
        pass


def _phase_error(phase, message):
    return {"phase": phase, "message": message}


def _describe_thrown(thrown):
    """Turns whatever a solution threw into the message sent to the suite side.

    The stack is deliberately not shipped: it names paths and line numbers the
    student must not learn from a hidden call.
    """
    if isinstance(thrown, BaseException):
        return thrown.args[0] if thrown.args else str(thrown)
    if isinstance(thrown, str):
        return thrown
    if thrown is not None:
        try:
            return json.dumps(thrown)
        except (TypeError, ValueError):
            pass
    return str(thrown)


def _serve_request(line, conn, write_lock, server):
    try:
        request = json.loads(line)
    except json.JSONDecodeError:
        _send(write_lock, conn, {"error": _phase_error(PHASES.BAD_REQUEST, "Malformed JSON on the shim socket")})
        return

    if not isinstance(request, dict):
        _send(write_lock, conn, {"error": _phase_error(PHASES.BAD_REQUEST, "A shim request must be an object")})
        return
    rid = request.get("id")
    # bool is a subclass of int in Python; a boolean id is not a numeric id.
    if not isinstance(rid, int) or isinstance(rid, bool):
        _send(write_lock, conn, {"error": _phase_error(PHASES.BAD_REQUEST, "A shim request needs a numeric id")})
        return
    method = request.get("method")
    if not isinstance(method, str):
        _send(
            write_lock,
            conn,
            {"id": rid, "error": _phase_error(PHASES.BAD_REQUEST, "A shim request needs a string method")},
        )
        return
    wire_args = request.get("args")
    if not isinstance(wire_args, list):
        _send(
            write_lock,
            conn,
            {"id": rid, "error": _phase_error(PHASES.BAD_REQUEST, "A shim request needs an array of args")},
        )
        return

    call_timeout_ms = server._call_timeout_ms
    namespace = server._namespace

    # Guarantees exactly one response line per request, even across a timeout race.
    class _Answer:
        __slots__ = ("lock", "sent")

        def __init__(self):
            self.lock = threading.Lock()
            self.sent = False

        def send(self, message):
            with self.lock:
                if self.sent:
                    return
                self.sent = True
            _send(write_lock, conn, message)

    answer = _Answer()
    timer_holder = []

    def invoke():
        try:
            args = deserialize(wire_args)
        except (TypeError, ValueError) as error:
            answer.send({"id": rid, "error": _phase_error(PHASES.NON_SERIALIZABLE_ARGS, str(error))})
            if timer_holder:
                timer_holder[0].cancel()
            return

        if method not in namespace:
            answer.send(
                {
                    "id": rid,
                    "error": _phase_error(PHASES.NO_SUCH_MEMBER, f"The submitted solution does not export '{method}'"),
                }
            )
            if timer_holder:
                timer_holder[0].cancel()
            return

        member = namespace[method]
        if not callable(member):
            answer.send(
                {"id": rid, "error": _phase_error(PHASES.NOT_CALLABLE, f"'{method}' is not callable by the suite")}
            )
            if timer_holder:
                timer_holder[0].cancel()
            return

        try:
            result = member(*args)
        except BaseException as error:  # noqa: BLE001 — a student may raise anything
            answer.send({"id": rid, "error": _phase_error(PHASES.INVOCATION_ERROR, _describe_thrown(error))})
            if timer_holder:
                timer_holder[0].cancel()
            return

        try:
            wire_result = serialize(result, "shim result")
        except (TypeError, ValueError) as error:
            answer.send({"id": rid, "error": _phase_error(PHASES.NON_SERIALIZABLE_RESULT, str(error))})
            if timer_holder:
                timer_holder[0].cancel()
            return

        answer.send({"id": rid, "result": wire_result})
        if timer_holder:
            timer_holder[0].cancel()

    def expire():
        answer.send(
            {
                "id": rid,
                "error": _phase_error(
                    PHASES.INVOCATION_TIMEOUT,
                    f"'{method}' exceeded the {call_timeout_ms}ms per-call shim limit",
                ),
            }
        )

    timer = threading.Timer(call_timeout_ms / 1000, expire)
    timer.daemon = True
    timer_holder.append(timer)
    threading.Thread(target=invoke, name=f"shim-call-{rid}", daemon=True).start()
    timer.start()


def _log_to_stderr(line):
    import sys

    print(line, file=sys.stderr)


def main():
    """Entry point for the grading container: serves the submitted solution."""
    socket_path = os.environ.get("SHIM_SOCKET", DEFAULT_SOCKET)
    solution_path = resolve_solution_path(os.environ.get("SOLUTION_PATH"), DEFAULT_SOLUTION_PATH)
    call_timeout_ms = int(os.environ.get("SHIM_CALL_TIMEOUT_MS", str(DEFAULT_CALL_TIMEOUT_MS)))

    try:
        server = create_shim_server(
            socket_path=socket_path, solution_path=solution_path, call_timeout_ms=call_timeout_ms,
            log=_log_to_stderr,
        )
    except ShimStartupError as error:
        _log_to_stderr(f"The grading shim server could not start: {error}")
        raise SystemExit(1) from error

    def shutdown(_signum, _frame):
        _log_to_stderr("shim server received shutdown signal")
        server.close()
        raise SystemExit(0)

    signal.signal(signal.SIGTERM, shutdown)
    signal.signal(signal.SIGINT, shutdown)
    # The server's threads are daemons, so the main thread must stay alive.
    signal.pause()


if __name__ == "__main__":
    main()
