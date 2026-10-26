// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

package gitgrader;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON read/write for the shim.
 *
 * <p>
 * The shim avoids any library dependency so it runs in any JVM without
 * needing to carry a JSON jar. The implementation is deliberately minimal: it
 * reads the NDJSON requests the suite sends and writes the responses, both in
 * the JSON subset the codec produces. It does not handle Unicode escapes,
 * backslash escapes beyond {@code \"}, {@code \\}, {@code \/}, {@code \b},
 * {@code \f}, {@code \n}, {@code \r}, {@code \t} or hex escapes, and it does
 * not validate string content in any depth. That is enough for the wire format
 * the codec produces.
 */
final class Json {

	private Json() {
	}

	/**
	 * Writes a value as a compact JSON string.
	 */
	static String write(Object value) {
		StringWriter out = new StringWriter();
		writeValue(out, value);
		return out.toString();
	}

	/**
	 * Reads a JSON value from a string.
	 */
	static Object read(String input) {
		return readValue(input, new int[] { 0 });
	}

	private static void writeValue(StringWriter out, Object value) {
		if (value == null) {
			out.write("null");
			return;
		}
		if (value instanceof String string) {
			out.write('"');
			for (int i = 0; i < string.length(); i++) {
				writeCodepoint(out, string.codePointAt(i));
			}
			out.write('"');
			return;
		}
		if (value instanceof Boolean bool) {
			out.write(bool ? "true" : "false");
			return;
		}
		if (value instanceof Number number) {
			out.write(number.toString());
			return;
		}
		if (value instanceof List<?> list) {
			out.write('[');
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					out.write(',');
				}
				writeValue(out, list.get(i));
			}
			out.write(']');
			return;
		}
		if (value instanceof Map<?, ?> map) {
			out.write('{');
			boolean first = true;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!first) {
					out.write(',');
				}
				first = false;
				writeValue(out, entry.getKey().toString());
				out.write(':');
				writeValue(out, entry.getValue());
			}
			out.write('}');
			return;
		}
		out.write("null");
	}

	private static void writeCodepoint(StringWriter out, int cp) {
		if (cp == '"' || cp == '\\') {
			out.write('\\');
			out.write(cp);
		}
		else if (cp == '\b') {
			out.write("\\b");
		}
		else if (cp == '\f') {
			out.write("\\f");
		}
		else if (cp == '\n') {
			out.write("\\n");
		}
		else if (cp == '\r') {
			out.write("\\r");
		}
		else if (cp == '\t') {
			out.write("\\t");
		}
		else if (cp < 0x20 || cp == 0x7f) {
			out.write(String.format("\\u%04x", cp));
		}
		else if (cp > 0xFFFF) {
			out.write(String.format("\\u%04x\\u%04x",
					(int) Character.highSurrogate(cp), (int) Character.lowSurrogate(cp)));
		}
		else {
			try {
				out.write(Character.toChars(cp));
			}
			catch (java.io.IOException error) {
				// A StringWriter never throws; the signature forces the check.
				throw new java.lang.IllegalStateException(error);
			}
		}
	}

	private static Object readValue(String input, int[] pos) {
		skipWhitespace(input, pos);
		if (pos[0] >= input.length()) {
			throw new Serializable.DeserializationException("Unexpected end of input");
		}
		char c = input.charAt(pos[0]);
		if (c == '"') {
			return readString(input, pos);
		}
		if (c == '-' || (c >= '0' && c <= '9')) {
			return readNumber(input, pos);
		}
		if (c == 't' && startsWith(input, pos[0], "true")) {
			pos[0] += 4;
			return Boolean.TRUE;
		}
		if (c == 'f' && startsWith(input, pos[0], "false")) {
			pos[0] += 5;
			return Boolean.FALSE;
		}
		if (c == 'n' && startsWith(input, pos[0], "null")) {
			pos[0] += 4;
			return null;
		}
		if (c == '[') {
			return readArray(input, pos);
		}
		if (c == '{') {
			return readObject(input, pos);
		}
		throw new Serializable.DeserializationException("Unexpected character at " + pos[0] + ": " + c);
	}

	private static String readString(String input, int[] pos) {
		pos[0]++; // skip opening "
		StringBuilder out = new StringBuilder();
		while (pos[0] < input.length()) {
			char c = input.charAt(pos[0]++);
			if (c == '"') {
				return out.toString();
			}
			if (c == '\\') {
				if (pos[0] >= input.length()) {
					throw new Serializable.DeserializationException("Unterminated string escape");
				}
				c = input.charAt(pos[0]++);
				switch (c) {
				case '"', '\\', '/' -> out.append(c);
				case 'b' -> out.append('\b');
				case 'f' -> out.append('\f');
				case 'n' -> out.append('\n');
				case 'r' -> out.append('\r');
				case 't' -> out.append('\t');
				case 'u' -> {
					int hex = Integer.parseInt(input.substring(pos[0], pos[0] + 4), 16);
					pos[0] += 4;
					out.append((char) hex);
				}
				default -> out.append('\\').append(c);
				}
			}
			else {
				out.append(c);
			}
		}
		throw new Serializable.DeserializationException("Unterminated string");
	}

	private static Object readNumber(String input, int[] pos) {
		int start = pos[0];
		if (input.charAt(start) == '-') {
			pos[0]++;
		}
		while (pos[0] < input.length() && input.charAt(pos[0]) >= '0' && input.charAt(pos[0]) <= '9') {
			pos[0]++;
		}
		if (pos[0] < input.length() && input.charAt(pos[0]) == '.') {
			pos[0]++;
			while (pos[0] < input.length() && input.charAt(pos[0]) >= '0' && input.charAt(pos[0]) <= '9') {
				pos[0]++;
			}
		}
		if (pos[0] < input.length() && (input.charAt(pos[0]) == 'e' || input.charAt(pos[0]) == 'E')) {
			pos[0]++;
			if (pos[0] < input.length() && (input.charAt(pos[0]) == '+' || input.charAt(pos[0]) == '-')) {
				pos[0]++;
			}
			while (pos[0] < input.length() && input.charAt(pos[0]) >= '0' && input.charAt(pos[0]) <= '9') {
				pos[0]++;
			}
		}
		String token = input.substring(start, pos[0]);
		if (token.contains(".") || token.contains("e") || token.contains("E")) {
			return Double.parseDouble(token);
		}
		try {
			return Integer.valueOf(token);
		}
		catch (NumberFormatException outOfIntRange) {
			// Ids and small numbers arrive as ints; anything wider stays a long.
			return Long.valueOf(token);
		}
	}

	private static List<Object> readArray(String input, int[] pos) {
		pos[0]++; // skip [
		List<Object> list = new ArrayList<>();
		skipWhitespace(input, pos);
		if (pos[0] < input.length() && input.charAt(pos[0]) == ']') {
			pos[0]++;
			return list;
		}
		while (pos[0] < input.length()) {
			list.add(readValue(input, pos));
			skipWhitespace(input, pos);
			if (pos[0] >= input.length() || input.charAt(pos[0]) != ',') {
				break;
			}
			pos[0]++;
		}
		skipWhitespace(input, pos);
		if (pos[0] < input.length() && input.charAt(pos[0]) == ']') {
			pos[0]++;
		}
		return list;
	}

	private static Map<String, Object> readObject(String input, int[] pos) {
		pos[0]++; // skip {
		Map<String, Object> map = new HashMap<>();
		skipWhitespace(input, pos);
		if (pos[0] < input.length() && input.charAt(pos[0]) == '}') {
			pos[0]++;
			return map;
		}
		while (pos[0] < input.length()) {
			skipWhitespace(input, pos);
			if (input.charAt(pos[0]) != '"') {
				break;
			}
			String key = (String) readValue(input, pos);
			skipWhitespace(input, pos);
			if (pos[0] < input.length() && input.charAt(pos[0]) == ':') {
				pos[0]++;
			}
			Object value = readValue(input, pos);
			map.put(key, value);
			skipWhitespace(input, pos);
			if (pos[0] >= input.length() || input.charAt(pos[0]) != ',') {
				break;
			}
			pos[0]++;
		}
		skipWhitespace(input, pos);
		if (pos[0] < input.length() && input.charAt(pos[0]) == '}') {
			pos[0]++;
		}
		return map;
	}

	private static void skipWhitespace(String input, int[] pos) {
		while (pos[0] < input.length() && " \t\n\r".indexOf(input.charAt(pos[0])) >= 0) {
			pos[0]++;
		}
	}

	private static boolean startsWith(String input, int offset, String prefix) {
		return input.regionMatches(offset, prefix, 0, prefix.length());
	}

}
