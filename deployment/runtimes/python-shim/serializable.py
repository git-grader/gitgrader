# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""
Value codec for the grading shim's NDJSON wire.

The wire is plain JSON, but a small set of Python values that JSON cannot
carry are represented by tagged markers, so the codec carries them faithfully
in both directions instead of refusing them:

- ``int`` outside JSON's double range becomes
  ``{"$gitgrader": ["bigint", "<decimal digits>"]}``
- an object whose ``__class__`` is not ``dict`` becomes
  ``{"$gitgrader": ["proto-object", <class name>, <encoded instance state>]}``

Everything else keeps its JSON shape: ``None``, booleans, finite numbers,
strings, lists, and dicts whose values are values. Values that a faithful
representation cannot carry at all are still rejected with :class:`TypeError`:
floats that are not finite (``nan``, ``inf``), callables, modules, and
circular structures. Rejecting by structural walk instead of by
``json.dumps`` is deliberate: ``json.dumps`` silently turns ``nan``/``inf``
into bare tokens and object identity into shared structure or data loss.
"""

MARKER = "$gitgrader"
KIND_BIGINT = "bigint"
KIND_PROTO_OBJECT = "proto-object"


class NonSerializable(TypeError):
    """Raised when a value cannot cross the shim faithfully."""


def serialize(value, what):
    """Encode *value* into its wire representation.

    :param value: the value to encode
    :param what: a label for the value, for error messages
    :returns: the wire representation, a JSON value
    :raises NonSerializable: when the value cannot cross the shim faithfully
    """
    return _encode(value, what, set())


def _encode(node, path, seen):
    if node is None or isinstance(node, (bool, str)):
        return node
    if isinstance(node, int):
        if -(2**53) < node < 2**53:
            return node
        return {MARKER: [KIND_BIGINT, str(node)]}
    if isinstance(node, float):
        if node != node or node in (float("inf"), float("-inf")):
            raise NonSerializable(
                f"{what} at {path} is {node!r}, which the shim cannot carry faithfully"
            )
        return node
    if isinstance(node, (bytes, bytearray)):
        raise NonSerializable(
            f"{what} at {path} is bytes, which cannot cross the shim"
        )
    if callable(node):
        raise NonSerializable(
            f"{what} at {path} is a callable, which cannot cross the shim"
        )
    if not isinstance(node, (dict, list)):
        raise NonSerializable(
            f"{what} at {path} is a {type(node).__name__}, which cannot cross the shim as a value"
        )
    marker = id(node)
    if marker in seen:
        raise NonSerializable(
            f"{what} at {path} is circular and cannot cross the shim"
        )
    seen.add(marker)
    try:
        if isinstance(node, list):
            return [_encode(child, f"{path}[{i}]", seen) for i, child in enumerate(node)]
        if type(node) is not dict:
            fields = {}
            for key, value in node.__dict__.items():
                if not isinstance(key, str):
                    raise NonSerializable(
                        f"{what} at {path} carries a non-string member name and cannot cross the shim"
                    )
                encoded = _encode(value, f"{path}.{key}", seen)
                fields[key] = encoded
            return {
                MARKER: [
                    KIND_PROTO_OBJECT,
                    type(node).__name__,
                    fields,
                ]
            }
        encoded = {}
        for key, value in node.items():
            if not isinstance(key, str):
                raise NonSerializable(
                    f"{what} at {path} carries a non-string key and cannot cross the shim"
                )
            encoded[key] = _encode(value, f"{path}.{key}", seen)
        return encoded
    finally:
        seen.discard(marker)


def deserialize(node):
    """Decode a parsed wire representation back into Python values.

    :param node: a value parsed from the wire
    :returns: the reconstructed Python value
    :raises TypeError: when the wire carries a malformed marker
    """
    if node is None or isinstance(node, (bool, str, float)):
        return node
    if isinstance(node, int):
        return node
    if isinstance(node, list):
        return [deserialize(item) for item in node]
    if isinstance(node, dict):
        keys = list(node.keys())
        if len(keys) == 1 and keys[0] == MARKER:
            return _decode_marker(node[MARKER])
        return {key: deserialize(value) for key, value in node.items()}
    raise TypeError(f"The shim cannot decode a value of type {type(node).__name__!r}")


def _decode_marker(marker):
    if isinstance(marker, list) and marker and isinstance(marker[0], str):
        kind, rest = marker[0], marker[1:]
        if kind == KIND_BIGINT and len(rest) == 1 and isinstance(rest[0], str):
            return int(rest[0])
        if kind == KIND_PROTO_OBJECT and len(rest) == 2 and isinstance(rest[1], dict):
            # Rebuilt as a simple namespace object: the fields carry the state
            # the suite asserted on, which is what a graded value is for.
            rebuilt = type(rest[0], (), {})()
            for key, value in rest[1].items():
                setattr(rebuilt, key, deserialize(value))
            return rebuilt
    raise TypeError("The shim wire carries a malformed value marker")