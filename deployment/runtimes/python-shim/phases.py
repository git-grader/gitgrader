# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""
The fixed vocabulary of a shim error's phase.

A phase is a stable, machine-readable reason; the message carries the human
detail. A stack trace is never transmitted across the socket: it is
instructor-only output logged by the suite side.
"""

BAD_REQUEST = "bad-request"
NO_SUCH_MEMBER = "no-such-member"
NOT_CALLABLE = "not-callable"
NON_SERIALIZABLE_ARGS = "non-serializable-args"
NON_SERIALIZABLE_RESULT = "non-serializable-result"
INVOCATION_ERROR = "invocation-error"
INVOCATION_TIMEOUT = "invocation-timeout"
CONNECT_TIMEOUT = "connect-timeout"
CONNECTION_CLOSED = "connection-closed"