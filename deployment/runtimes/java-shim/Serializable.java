/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The shim's value codec: encodes values into the wire representation and back.
 *
 * <p>
 * The wire is plain JSON carrying null, Boolean, finite Number, String, List
 * and Map values, plus one tagged marker for values JSON cannot carry:
 * {@code byte[]} becomes {@code {"$gitgrader": ["bytes", "<base64>"]}}. A value
 * the contract cannot carry faithfully is refused rather than converted.
 */
final class Serializable {

	static final String MARKER = "$gitgrader";
	static final String KIND_BYTES = "bytes";

	private Serializable() {
	}

	static Object encode(Object node) {
		return encodeAt(node, new java.util.HashSet<>());
	}

	/** Encodes a Java value into its JSON wire form. */
	private static Object encodeAt(Object node, java.util.Set<Integer> seen) {
		if (node == null) {
			return null;
		}
		if (node instanceof String || node instanceof Boolean) {
			return node;
		}
		if (node instanceof Integer || node instanceof Long) {
			return node;
		}
		if (node instanceof Short || node instanceof Byte) {
			return ((Number) node).intValue();
		}
		if (node instanceof Double || node instanceof Float) {
			double value = ((Number) node).doubleValue();
			if (!Double.isFinite(value)) {
				throw new IllegalArgumentException("a value is " + value + ", which the shim cannot carry faithfully");
			}
			return value;
		}
		if (node instanceof byte[]) {
			return marker(KIND_BYTES, Base64.getEncoder().encodeToString((byte[]) node));
		}
		if (node instanceof int[] || node instanceof long[] || node instanceof double[] || node instanceof String[]) {
			Object[] array = (Object[]) null;
			Object raw = node;
			int length = 0;
			if (raw instanceof int[]) {
				int[] values = (int[]) raw;
				length = values.length;
			}
			List<Object> encoded = new ArrayList<>(length);
			if (raw instanceof int[]) {
				for (int value : (int[]) raw) {
					encoded.add(value);
				}
			}
			else if (raw instanceof long[]) {
				for (long value : (long[]) raw) {
					encoded.add(value);
				}
			}
			else if (raw instanceof double[]) {
				for (double value : (double[]) raw) {
					encoded.add(value);
				}
			}
			else {
				for (String value : (String[]) raw) {
					encoded.add(value);
				}
			}
			return encoded;
		}
		if (node instanceof List<?> list) {
			checkCycle(list, seen);
			List<Object> encoded = new ArrayList<>(list.size());
			for (Object child : list) {
				encoded.add(encodeAt(child, seen));
			}
			seen.remove(System.identityHashCode(list));
			return encoded;
		}
		if (node instanceof Map<?, ?> map) {
			checkCycle(map, seen);
			Map<String, Object> encoded = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!(entry.getKey() instanceof String key)) {
					throw new IllegalArgumentException("an object carries a non-string key and cannot cross the shim");
				}
				encoded.put(key, encodeAt(entry.getValue(), seen));
			}
			seen.remove(System.identityHashCode(map));
			return encoded;
		}
		throw new IllegalArgumentException(
				"a value of type " + node.getClass().getSimpleName() + " cannot cross the shim as a value");
	}

	private static void checkCycle(Object node, java.util.Set<Integer> seen) {
		if (!seen.add(System.identityHashCode(node))) {
			throw new IllegalArgumentException("a value is circular and cannot cross the shim");
		}
	}

	static Map<String, Object> marker(String kind, Object payload) {
		Map<String, Object> marker = new LinkedHashMap<>();
		List<Object> body = new ArrayList<>();
		body.add(kind);
		body.add(payload);
		marker.put(MARKER, body);
		return marker;
	}

	/** Decodes a parsed wire value back into Java values. */
	static Object decode(Object node) {
		if (node == null || node instanceof String || node instanceof Boolean
				|| node instanceof Integer || node instanceof Long || node instanceof Double) {
			return node;
		}
		if (node instanceof List<?> list) {
			List<Object> decoded = new ArrayList<>(list.size());
			for (Object child : list) {
				decoded.add(decode(child));
			}
			return decoded;
		}
		if (node instanceof Map<?, ?> map) {
			if (map.size() == 1 && map.containsKey(MARKER)) {
				return decodeMarker(map.get(MARKER));
			}
			Map<String, Object> decoded = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				decoded.put(String.valueOf(entry.getKey()), decode(entry.getValue()));
			}
			return decoded;
		}
		throw new IllegalArgumentException("a value of unsupported type cannot cross the shim as a value");
	}

	private static Object decodeMarker(Object marker) {
		if (marker instanceof List<?> body && !body.isEmpty() && body.get(0) instanceof String kind) {
			if (KIND_BYTES.equals(kind) && body.size() == 2 && body.get(1) instanceof String encoded) {
				return Base64.getDecoder().decode(encoded);
			}
		}
		throw new IllegalArgumentException("the wire carries a malformed value marker");
	}
}
