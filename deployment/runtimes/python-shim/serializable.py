# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""The shim's value codec.

Values still travel as JSON on the wire. A small set of values JSON cannot
carry is represented by tagged markers instead of being silently mangled:

- ``tuple`` becomes ``{"$gitgrader": ["tuple", [...]]}``
- ``bytes`` becomes ``{"$gitgrader": ["bytes", "<base64>"]}``

Everything else keeps its natural JSON form. A value the contract cannot carry
faithfully -- a non-finite float, a set, an arbitrary object, a cycle -- is
refused with :class:`TypeError` rather than converted.
"""

import base64
import math

MARKER = "$gitgrader"
KIND_TUPLE = "tuple"
KIND_BYTES = "bytes"


def serialize(value, what):
    """Encodes a value into its wire representation.

    :param value: the value to encode
    :param what: a label for the value, used as the root of diagnostic paths
    :returns: plain JSON-compatible data
    :raises TypeError: when the value cannot cross the shim faithfully
    """
    return _encode(value, what, set())


def _encode(node, path, seen):
    if node is None or isinstance(node, (str, bool, int)):
        return node
    if isinstance(node, float):
        if not math.isfinite(node):
            raise TypeError(f"{path} is {node}, which the shim cannot carry faithfully")
        return node
    if isinstance(node, bytes):
        return {MARKER: [KIND_BYTES, base64.b64encode(node).decode("ascii")]}
    if isinstance(node, tuple):
        return {
            MARKER: [
                KIND_TUPLE,
                _encode_sequence(node, path, seen),
            ]
        }
    if isinstance(node, list):
        return _encode_sequence(node, path, seen)
    if isinstance(node, dict):
        _guard_cycle(node, path, seen)
        try:
            encoded = {}
            for key, child in node.items():
                if not isinstance(key, str):
                    raise TypeError(f"{path} has a non-string key {key!r}, which cannot cross the shim")
                encoded[key] = _encode(child, f"{path}.{key}", seen)
            return encoded
        finally:
            seen.discard(id(node))
    raise TypeError(f"{path} is a {type(node).__name__} and cannot cross the shim as a value")


def _encode_sequence(node, path, seen):
    _guard_cycle(node, path, seen)
    try:
        return [_encode(child, f"{path}[{index}]", seen) for index, child in enumerate(node)]
    finally:
        seen.discard(id(node))


def _guard_cycle(node, path, seen):
    if id(node) in seen:
        raise TypeError(f"{path} is circular and cannot cross the shim")
    seen.add(id(node))


def deserialize(node):
    """Decodes a parsed wire representation back into Python values.

    :param node: a value parsed from the wire
    :returns: the reconstructed value
    :raises TypeError: when the wire carries a malformed marker
    """
    if node is None or isinstance(node, (str, bool, int, float)):
        return node
    if isinstance(node, list):
        return [deserialize(child) for child in node]
    if not isinstance(node, dict):
        raise TypeError(f"The shim cannot decode a value of type '{type(node).__name__}'")
    if len(node) == 1 and MARKER in node:
        return _decode_marker(node[MARKER])
    return {key: deserialize(child) for key, child in node.items()}


def _decode_marker(marker):
    if isinstance(marker, list) and marker and isinstance(marker[0], str):
        kind, rest = marker[0], marker[1:]
        if kind == KIND_TUPLE and len(rest) == 1 and isinstance(rest[0], list):
            return tuple(deserialize(child) for child in rest[0])
        if kind == KIND_BYTES and len(rest) == 1 and isinstance(rest[0], str):
            try:
                return base64.b64decode(rest[0], validate=True)
            except ValueError as error:
                raise TypeError(f"The shim wire carries undecodable bytes: {error}") from error
    raise TypeError("The shim wire carries a malformed value marker")
