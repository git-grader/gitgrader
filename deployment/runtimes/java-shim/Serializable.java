// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

package gitgrader;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Value codec for the grading shim's NDJSON wire.
 *
 * <p>
 * The wire is plain JSON, but values JSON cannot carry are represented by
 * tagged markers, so the codec carries them faithfully in both directions
 * instead of refusing them:
 *
 * <ul>
 * <li>a {@code long} or {@code BigInteger} outside JSON's double range becomes
 * {@code {"$gitgrader": {"kind": "bigint", "value": "<digits>"}}}</li>
 * <li>an instance that is none of the JSON-shaped types above is carried as
 * {@code {"$gitgrader": {"kind": "proto-object", "class": ..., "fields": ...}}}
 * and rebuilt reflectively on the suite side</li>
 * </ul>
 *
 * <p>
 * Everything else keeps its JSON shape: null, booleans, finite numbers,
 * strings, lists, and maps. Values that a faithful representation cannot carry
 * at all are still rejected: NaN and Infinity (JSON has no spelling for them),
 * non-string map keys, and circular structures. Rejecting by structural walk
 * is deliberate: a silent lossy conversion would fail an assertion far from
 * the value that caused it.
 */
public final class Serializable {

	/** The key that marks a special-value envelope on the wire. */
	static final String MARKER = "$gitgrader";

	private static final String KIND_BIGINT = "bigint";

	private static final String KIND_PROTO_OBJECT = "proto-object";

	/** Largest positive integer JSON numbers can round-trip exactly. */
	private static final long JSON_SAFE_MAX = 1L << 53;

	/** Smallest negative integer JSON numbers can round-trip exactly. */
	private static final long JSON_SAFE_MIN = -(1L << 53);

	private Serializable() {
	}

	/**
	 * Encodes a value into its wire representation.
	 * @param value the value to encode
	 * @param what a label for the value, for error messages
	 * @return the wire representation, a JSON value
	 * @throws NonSerializableException when the value cannot cross the shim
	 * faithfully
	 */
	public static Object serialize(Object value, String what) {
		return encode(value, what, new IdentityHashMap<>());
	}

	/**
	 * Decodes a parsed wire representation back into Java values, inverting
	 * {@link #serialize}: marker maps become {@code BigInteger} and rebuilt
	 * objects; everything else is returned as-is.
	 * @param node a value parsed from the wire
	 * @return the reconstructed Java value
	 * @throws DeserializationException when the wire carries a malformed
	 * marker
	 */
	@SuppressWarnings("unchecked")
	public static Object deserialize(Object node) {
		if (node == null || node instanceof String || node instanceof Boolean) {
			return node;
		}
		if (node instanceof Number || node instanceof Character) {
			// JSON numbers decode as Integer, Long or Double depending on their
			// spelling; all three are already the value the caller meant.
			return node;
		}
		if (node instanceof List<?> list) {
			List<Object> decoded = new ArrayList<>();
			for (Object item : list) {
				decoded.add(deserialize(item));
			}
			return decoded;
		}
		if (node instanceof Map<?, ?> map) {
			if (map.size() == 1 && map.containsKey(MARKER)) {
				return decodeMarker(map.get(MARKER));
			}
			Map<String, Object> decoded = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!(entry.getKey() instanceof String key)) {
					throw new DeserializationException("The shim cannot decode a non-string map key");
				}
				decoded.put(key, deserialize(entry.getValue()));
			}
			return decoded;
		}
		throw new DeserializationException("The shim cannot decode a value of type " + node.getClass().getName());
	}

	// The encode method is one recursive closure so `what` stays in scope
	// everywhere without threading through every helper.
	private static Object encode(Object node, String what, IdentityHashMap<Object, Boolean> seen) {
		if (node == null || node instanceof String || node instanceof Boolean) {
			return node;
		}
		if (node instanceof Character character) {
			return character.toString();
		}
		if (node instanceof Integer || node instanceof Short || node instanceof Byte) {
			return ((Number) node).intValue();
		}
		if (node instanceof Long longValue) {
			if (longValue >= JSON_SAFE_MIN && longValue <= JSON_SAFE_MAX) {
				return longValue;
			}
			return marker(KIND_BIGINT, longValue.toString());
		}
		if (node instanceof BigInteger big) {
			return marker(KIND_BIGINT, big.toString());
		}
		if (node instanceof Float || node instanceof Double) {
			double doubleValue = ((Number) node).doubleValue();
			if (Double.isNaN(doubleValue) || Double.isInfinite(doubleValue)) {
				throw new NonSerializableException(
						what + " at _ is " + doubleValue + ", which the shim cannot carry faithfully");
			}
			return doubleValue;
		}
		if (node instanceof Iterable<?> list) {
			if (seen.putIfAbsent(node, Boolean.TRUE) != null) {
				throw new NonSerializableException(what + " at _ is circular and cannot cross the shim");
			}
			List<Object> encoded = new ArrayList<>();
			int index = 0;
			for (Object child : list) {
				encoded.add(encode(child, what + "[" + index + "]", seen));
				index++;
			}
			seen.remove(node);
			return encoded;
		}
		if (node instanceof Map<?, ?> map) {
			if (seen.putIfAbsent(node, Boolean.TRUE) != null) {
				throw new NonSerializableException(what + " at _ is circular and cannot cross the shim");
			}
			Map<String, Object> encoded = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!(entry.getKey() instanceof String key)) {
					throw new NonSerializableException(
							what + " at _ carries a non-string key and cannot cross the shim");
				}
				encoded.put(key, encode(entry.getValue(), what + "." + key, seen));
			}
			seen.remove(node);
			return encoded;
		}
		if (node instanceof Object[] array) {
			if (seen.putIfAbsent(node, Boolean.TRUE) != null) {
				throw new NonSerializableException(what + " at _ is circular and cannot cross the shim");
			}
			List<Object> encoded = new ArrayList<>();
			for (int i = 0; i < array.length; i++) {
				encoded.add(encode(array[i], what + "[" + i + "]", seen));
			}
			seen.remove(node);
			return encoded;
		}
		if (node instanceof Class<?> || node instanceof ClassLoader || node instanceof Thread) {
			throw new NonSerializableException(
					what + " at _ is a " + node.getClass().getSimpleName() + " and cannot cross the shim");
		}
		// Anything else: record objects, solution-defined classes, boxed types
		// the codec does not name above. The fields carry the state the suite
		// asserted on, which is what a graded value is for.
		return encodeAsProtoObject(node, what, seen);
	}

	private static Object encodeAsProtoObject(Object node, String what, IdentityHashMap<Object, Boolean> seen) {
		if (seen.putIfAbsent(node, Boolean.TRUE) != null) {
			throw new NonSerializableException(what + " at _ is circular and cannot cross the shim");
		}
		Map<String, Object> fields = new LinkedHashMap<>();
		Class<?> type = node.getClass();
		while (type != null && type != Object.class) {
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers())) {
					continue;
				}
				field.setAccessible(true);
				try {
					fields.put(field.getName(), encode(field.get(node), what + "." + field.getName(), seen));
				}
				catch (IllegalAccessException error) {
					throw new NonSerializableException(
							what + "." + field.getName() + " could not be read for the wire");
				}
			}
			type = type.getSuperclass();
		}
		seen.remove(node);
		Map<String, Object> marker = new LinkedHashMap<>();
		marker.put("kind", KIND_PROTO_OBJECT);
		marker.put("class", node.getClass().getName());
		marker.put("fields", fields);
		Map<String, Object> wrapped = new LinkedHashMap<>();
		wrapped.put(MARKER, marker);
		return wrapped;
	}

	private static Map<String, Object> marker(String kind, String payload) {
		Map<String, Object> marker = new LinkedHashMap<>();
		marker.put("kind", kind);
		marker.put("value", payload);
		Map<String, Object> wrapped = new LinkedHashMap<>();
		wrapped.put(MARKER, marker);
		return wrapped;
	}

	@SuppressWarnings("unchecked")
	private static Object decodeMarker(Object wrapped) {
		if (wrapped instanceof Map<?, ?> marker) {
			if (KIND_BIGINT.equals(marker.get("kind")) && marker.get("value") instanceof String digits) {
				return new BigInteger(digits);
			}
			if (KIND_PROTO_OBJECT.equals(marker.get("kind"))) {
				return rebuildProtoObject(marker.get("class"), marker.get("fields"));
			}
		}
		throw new DeserializationException("The shim wire carries a malformed value marker");
	}

	@SuppressWarnings("unchecked")
	private static Object rebuildProtoObject(Object classNameValue, Object fieldsValue) {
		if (!(classNameValue instanceof String className) || !(fieldsValue instanceof Map<?, ?> fieldMap)) {
			throw new DeserializationException("The shim wire carries a malformed proto-object marker");
		}
		Map<String, Object> decoded = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : fieldMap.entrySet()) {
			if (!(entry.getKey() instanceof String key)) {
				throw new DeserializationException("The shim wire carries a malformed proto-object field");
			}
			decoded.put(key, deserialize(entry.getValue()));
		}
		return rebuild(className, decoded);
	}

	@SuppressWarnings("unchecked")
	private static Object rebuild(String className, Map<String, Object> decoded) {
		ClassLoader loader = Thread.currentThread().getContextClassLoader();
		try {
			Class<?> type = Class.forName(className, false, loader);
			// Rebuild via a no-arg constructor, then set each field reflectively.
			// The constructor must exist; solutions can always provide one.
			java.lang.reflect.Constructor<?> noArgs = null;
			for (java.lang.reflect.Constructor<?> ctor : type.getDeclaredConstructors()) {
				if (ctor.getParameterCount() == 0) {
					noArgs = ctor;
					break;
				}
			}
			if (noArgs == null) {
				return asFieldMap(className, decoded);
			}
			noArgs.setAccessible(true);
			Object instance = noArgs.newInstance();
			for (Field field : type.getDeclaredFields()) {
				if (Modifier.isStatic(field.getModifiers()) || !decoded.containsKey(field.getName())) {
					continue;
				}
				field.setAccessible(true);
				field.set(instance, decoded.get(field.getName()));
			}
			return instance;
		}
		catch (ReflectiveOperationException | RuntimeException error) {
			// The suite cannot rebuild a class it cannot load; the fields are
			// still delivered, under the class name, so an assertion can reach
			// the value even without the type.
			return asFieldMap(className, decoded);
		}
	}

	private static Map<String, Object> asFieldMap(String className, Map<String, Object> fields) {
		Map<String, Object> wrapped = new LinkedHashMap<>();
		wrapped.put("class", className);
		wrapped.put("fields", fields);
		return wrapped;
	}

	/** Raised when a value cannot cross the shim faithfully. */
	public static final class NonSerializableException extends RuntimeException {

		NonSerializableException(String message) {
			super(message);
		}

	}

	/** Raised when a wire value cannot be decoded back. */
	public static final class DeserializationException extends RuntimeException {

		DeserializationException(String message) {
			super(message);
		}

	}

}
