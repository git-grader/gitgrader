# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""Unix-socket shim client used by the hidden suite side.

Connects to the sandbox's shim socket and exposes the submitted solution's
callable exports as attributes whose calls become awaited socket calls. The
server may answer out of order (calls run concurrently up to the per-call
ceiling), so responses are correlated by id, never by stream order.
"""

import json
import socket
import threading
import time

from phases import PHASES
from serializable import deserialize, serialize

DEFAULT_SOCKET = "/gitgrader-shim/runner.sock"
DEFAULT_CONNECT_TIMEOUT_MS = 30_000

RETRY_DELAY_MS = 75


class ShimError(Exception):
    """An error that crossed the shim boundary as a protocol error.

    ``phase`` is the stable machine-readable reason; ``message`` the detail.
    The name is set to the phase so test runner output carries the same
    vocabulary the protocol documents.
    """

    def __init__(self, phase, message):
        super().__init__(message)
        self.phase = phase
        self.name = phase


class ShimClient:
    """A proxy of the submitted solution's exports over the shim socket.

    Attributes are forwarded as calls: ``client.truncate("ab", 1)`` invokes the
    export ``truncate`` on the sandbox side with those arguments and blocks
    until the answer arrives. An attribute access is not a call by itself; only
    invoking it travels the socket, so a suite can name an export without
    touching the sandbox.
    """

    def __init__(self, socket_path=DEFAULT_SOCKET, connect_timeout_ms=DEFAULT_CONNECT_TIMEOUT_MS):
        self._socket_path = socket_path
        self._connect_timeout_ms = connect_timeout_ms
        self._next_id = 0
        self._id_lock = threading.Lock()
        self._write_lock = threading.Lock()
        self._pending = {}
        self._pending_lock = threading.Lock()
        self._closed = False
        self._socket = _connect_with_retry(socket_path, connect_timeout_ms)
        self._reader = threading.Thread(target=self._read_loop, daemon=True, name="shim-client-reader")
        self._reader.start()

    def __getattr__(self, name):
        if name.startswith("_"):
            raise AttributeError(name)
        return _BoundMethod(self, name)

    def call(self, method, *args):
        if self._closed:
            raise ShimError(PHASES.CONNECTION_CLOSED, f"The shim connection to {self._socket_path} is closed")
        try:
            wire_args = serialize(list(args), "shim arguments")
        except (TypeError, ValueError) as error:
            raise ShimError(PHASES.NON_SERIALIZABLE_ARGS, str(error)) from error

        with self._id_lock:
            self._next_id += 1
            rid = self._next_id

        event = threading.Event()
        holder = {"response": None}

        with self._pending_lock:
            self._pending[rid] = (event, holder)

        try:
            message = json.dumps({"id": rid, "method": method, "args": wire_args})
            with self._write_lock:
                self._socket.sendall((message + "\n").encode("utf-8", errors="replace"))
        except OSError as error:
            with self._pending_lock:
                self._pending.pop(rid, None)
            raise ShimError(PHASES.CONNECTION_CLOSED, str(error)) from error

        event.wait()
        marker, value = holder["response"]
        if marker == "result":
            return value
        raise ShimError(*value)

    def close(self):
        if self._closed:
            return
        self._closed = True
        try:
            self._socket.close()
        except OSError:
            pass
        with self._pending_lock:
            pending = list(self._pending.values())
            self._pending.clear()
        for event, holder in pending:
            holder["response"] = ("error", (PHASES.CONNECTION_CLOSED, "The shim client was closed"))
            event.set()

    def _read_loop(self):
        buf = bytearray()
        while True:
            try:
                chunk = self._socket.recv(4096)
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
                try:
                    message = json.loads(line_bytes.decode("utf-8", errors="replace"))
                except (json.JSONDecodeError, UnicodeDecodeError):
                    continue
                if not isinstance(message, dict) or not isinstance(message.get("id"), int):
                    continue
                rid = message["id"]
                with self._pending_lock:
                    entry = self._pending.pop(rid, None)
                if entry is None:
                    continue
                event, holder = entry
                if "result" in message:
                    try:
                        value = deserialize(message["result"])
                    except TypeError as error:
                        holder["response"] = ("error", (PHASES.BAD_REQUEST, str(error)))
                    else:
                        holder["response"] = ("result", value)
                elif isinstance(message.get("error"), dict):
                    error = message["error"]
                    holder["response"] = (
                        "error",
                        (
                            error.get("phase") or PHASES.BAD_REQUEST,
                            str(error.get("message") or ""),
                        ),
                    )
                else:
                    holder["response"] = ("error", (PHASES.BAD_REQUEST, "The shim reply carried neither result nor error"))
                event.set()
        # Socket closed underneath us; fail anything still pending.
        with self._pending_lock:
            pending = list(self._pending.values())
            self._pending.clear()
        self._closed = True
        for event, holder in pending:
            holder["response"] = (
                "error",
                (
                    PHASES.CONNECTION_CLOSED,
                    f"The shim connection to {self._socket_path} closed before all calls had answered",
                ),
            )
            event.set()


class _BoundMethod:
    """One named export on a :class:`ShimClient`, bound to that client."""

    __slots__ = ("client", "name")

    def __init__(self, client, name):
        self.client = client
        self.name = name

    def __call__(self, *args):
        return self.client.call(self.name, *args)


def create_shim_client(socket_path=DEFAULT_SOCKET, connect_timeout_ms=DEFAULT_CONNECT_TIMEOUT_MS):
    """Connects a shim client to a running sandbox.

    :param socket_path: the Unix socket the sandbox listens on
    :param connect_timeout_ms: deadline in milliseconds; the sandbox creates
        its socket shortly after the runner starts it, so the first connect
        routinely races it
    :returns: a :class:`ShimClient` exposing the solution's exports
    :raises ShimError: with phase ``connect-timeout`` when the deadline passes
    """
    return ShimClient(socket_path=socket_path, connect_timeout_ms=connect_timeout_ms)


def _connect_with_retry(socket_path, connect_timeout_ms):
    deadline = time.monotonic() + connect_timeout_ms / 1000
    while True:
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            sock.connect(socket_path)
            return sock
        except OSError as error:
            sock.close()
            if time.monotonic() >= deadline:
                raise ShimError(
                    PHASES.CONNECT_TIMEOUT,
                    f"Could not reach the grading shim at {socket_path} within {connect_timeout_ms}ms",
                ) from error
            time.sleep(RETRY_DELAY_MS / 1000)
