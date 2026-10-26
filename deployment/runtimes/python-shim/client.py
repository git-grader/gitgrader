# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""
Connects the suite side of the shim to the sandbox side.

The sandbox container starts first and creates the socket, so the first
connect from the suite container routinely races it: the client keeps trying
until the connect timeout has passed. Once connected, :func:`create_shim_client`
returns a proxy of the solution's exports, where every attribute access
becomes a call that the suite awaits and that raises :class:`ShimError` when
the call or the socket fails. Non-serialisable arguments are refused before
they are sent, so a suite bug cannot tear the stream.
"""

import json
import os
import socket
import threading
import time

from phases import (
    BAD_REQUEST,
    CONNECTION_CLOSED,
    CONNECT_TIMEOUT,
    NON_SERIALIZABLE_ARGS,
)
from serializable import NonSerializable, deserialize, serialize

DEFAULT_SOCKET = "/gitgrader-shim/runner.sock"
DEFAULT_CONNECT_TIMEOUT_MS = 30_000
RETRY_DELAY_SECONDS = 0.075


class ShimError(Exception):
    """An error that crossed the shim boundary as a protocol error.

    ``phase`` is the stable machine-readable reason; ``message`` the detail.
    The name is set to the phase so test runner output carries the same
    vocabulary the protocol documents.
    """

    def __init__(self, phase, message):
        super().__init__(message)
        self.phase = phase
        self.message = message


class _PendingCall:
    """One in-flight call awaiting its correlated response."""

    __slots__ = ("condition", "done", "error", "result")

    def __init__(self):
        self.condition = threading.Condition()
        self.done = False
        self.result = None
        self.error = None


class _ShimProxy:
    """Attribute access becomes a remote call into the submission."""

    def __init__(self, call):
        object.__setattr__(self, "_call", call)

    def __getattr__(self, name):
        def call(*args):
            return object.__getattribute__(self, "_call")(name, list(args))

        return call


def _connect_with_retry(socket_path, connect_timeout_ms):
    deadline = time.monotonic() + connect_timeout_ms / 1000
    while True:
        sock = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            sock.settimeout(1.0)
            sock.connect(socket_path)
            return sock
        except OSError:
            sock.close()
            if time.monotonic() >= deadline:
                raise ShimError(
                    CONNECT_TIMEOUT,
                    f"Could not reach the grading shim at {socket_path} "
                    f"within {connect_timeout_ms}ms",
                )
            time.sleep(RETRY_DELAY_SECONDS)


def create_shim_client(socket_path=None, connect_timeout_ms=None):
    """Connect the suite side of the shim to the sandbox side.

    :param socket_path: the socket on the shared volume; defaults to
        ``SHIM_SOCKET`` and then the canonical runner socket
    :param connect_timeout_ms: how long connecting may take; defaults to
        ``SHIM_CONNECT_TIMEOUT_MS`` and then 30 seconds
    :returns: ``(proxy, close)`` — the proxy and a shutdown handle
    :raises ShimError: with phase ``connect-timeout`` when the deadline passes
    """
    if socket_path is None:
        socket_path = os.environ.get("SHIM_SOCKET", DEFAULT_SOCKET)
    if connect_timeout_ms is None:
        connect_timeout_ms = int(os.environ.get("SHIM_CONNECT_TIMEOUT_MS", DEFAULT_CONNECT_TIMEOUT_MS))

    sock = _connect_with_retry(socket_path, connect_timeout_ms)
    sock.settimeout(None)
    connection = _ShimConnection(sock, socket_path)
    reader = threading.Thread(target=connection._read_loop, daemon=True)
    reader.start()
    return _ShimProxy(connection.call), connection.close


class _ShimConnection:
    """One connected client: correlated request/response over the socket."""

    def __init__(self, sock, socket_path):
        self._sock = sock
        self._socket_path = socket_path
        self._mutex = threading.Lock()
        self._pending = {}
        self._next_id = 1
        self._buffer = b""
        self._closed = False

    def call(self, method, args):
        if self._closed:
            raise ShimError(
                CONNECTION_CLOSED,
                f"The shim connection to {self._socket_path} is closed",
            )
        try:
            wire_args = [serialize(arg, "shim arguments") for arg in args]
        except NonSerializable as error:
            raise ShimError(NON_SERIALIZABLE_ARGS, str(error)) from error

        with self._mutex:
            if self._closed:
                raise ShimError(
                    CONNECTION_CLOSED,
                    f"The shim connection to {self._socket_path} is closed",
                )
            call_id = self._next_id
            self._next_id += 1
            pending = _PendingCall()
            self._pending[call_id] = pending

        line = (json.dumps({"id": call_id, "method": method, "args": wire_args}) + "\n").encode("utf-8")
        try:
            self._sock.sendall(line)
        except OSError:
            with self._mutex:
                self._pending.pop(call_id, None)
            raise ShimError(
                CONNECTION_CLOSED,
                f"Could not write the call to '{method}' on the shim "
                f"connection to {self._socket_path}",
            )
        return self._await(pending)

    def _await(self, pending):
        with pending.condition:
            while not pending.done and not self._closed:
                pending.condition.wait()
        if pending.error is not None:
            raise pending.error
        if not pending.done:
            raise ShimError(
                CONNECTION_CLOSED,
                f"The shim connection to {self._socket_path} closed "
                "before all calls had answered",
            )
        return pending.result

    def _read_loop(self):
        while True:
            try:
                chunk = self._sock.recv(65536)
            except OSError:
                chunk = b""
            if not chunk:
                self._fail_pending(
                    f"The shim connection to {self._socket_path} closed "
                    "before all calls had answered"
                )
                return
            self._buffer += chunk
            while True:
                newline = self._buffer.find(b"\n")
                if newline == -1:
                    break
                line = self._buffer[:newline]
                self._buffer = self._buffer[newline + 1 :]
                if line.strip():
                    self._dispatch(line)

    def _dispatch(self, line):
        try:
            message = json.loads(line)
        except ValueError:
            return
        if not isinstance(message, dict):
            return
        call_id = message.get("id")
        with self._mutex:
            pending = self._pending.pop(call_id, None) if isinstance(call_id, int) else None
        if pending is None or pending.done:
            return
        if "result" in message:
            pending.result = deserialize(message["result"])
            pending.done = True
        elif message.get("error"):
            error = message["error"]
            pending.error = ShimError(error.get("phase", BAD_REQUEST), str(error.get("message", "")))
            pending.done = True
        else:
            pending.error = ShimError(BAD_REQUEST, "The shim response carried neither a result nor an error")
            pending.done = True
        with pending.condition:
            pending.condition.notify_all()

    def _fail_pending(self, message):
        with self._mutex:
            pending_calls = list(self._pending.values())
            self._pending.clear()
        for pending in pending_calls:
            if not pending.done:
                pending.error = ShimError(CONNECTION_CLOSED, message)
                pending.done = True
                with pending.condition:
                    pending.condition.notify_all()

    def close(self):
        if self._closed:
            return
        self._closed = True
        try:
            self._sock.shutdown(socket.SHUT_RDWR)
        except OSError:
            pass
        self._sock.close()
        with self._mutex:
            pending_calls = list(self._pending.values())
            self._pending.clear()
        for pending in pending_calls:
            if not pending.done:
                pending.error = ShimError(CONNECTION_CLOSED, "The shim client was closed")
                pending.done = True
                with pending.condition:
                    pending.condition.notify_all()