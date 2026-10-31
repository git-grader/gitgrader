/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader and writer for the shim wire.
 *
 * <p>
 * The wire carries exactly the values the value contract allows, so this is a
 * deliberately small implementation: objects, arrays, strings, finite numbers,
 * booleans and null. It exists so the sandbox side needs no JSON library beyond
 * the JDK; the suite side (JUnit) may use any.
 */
final class Json {

	private Json() {
	}

	/** Parses one JSON value. */
	static Object parse(String text) {
		Reader reader = new Reader(text);
		Object value = reader.value();
		reader.end();
		return value;
	}

	/** Writes one JSON value. */
	static String write(Object value) {
		StringBuilder out = new StringBuilder();
		write(out, value);
		return out.toString();
	}

	private static void write(StringBuilder out, Object value) {
		if (value == null) {
			out.append("null");
		}
		else if (value instanceof String string) {
			quote(out, string);
		}
		else if (value instanceof Boolean || value instanceof Integer || value instanceof Long) {
			out.append(value);
		}
		else if (value instanceof Double) {
			out.append(doubleToString((Double) value));
		}
		else if (value instanceof List<?> list) {
			out.append('[');
			for (int i = 0; i < list.size(); i++) {
				if (i > 0) {
					out.append(',');
				}
				write(out, list.get(i));
			}
			out.append(']');
		}
		else if (value instanceof Map<?, ?> map) {
			out.append('{');
			boolean first = true;
			for (Map.Entry<?, ?> entry : map.entrySet()) {
				if (!first) {
					out.append(',');
				}
				first = false;
				quote(out, String.valueOf(entry.getKey()));
				out.append(':');
				write(out, entry.getValue());
			}
			out.append('}');
		}
		else {
			throw new IllegalArgumentException("cannot serialise " + value.getClass().getSimpleName() + " to JSON");
		}
	}

	private static String doubleToString(double value) {
		if (value == Math.rint(value) && Math.abs(value) < 1e15) {
			return String.valueOf((long) value);
		}
		return Double.toString(value);
	}

	private static void quote(StringBuilder out, String text) {
		out.append('"');
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			switch (c) {
				case '"' -> out.append("\\\"");
				case '\\' -> out.append("\\\\");
				case '\n' -> out.append("\\n");
				case '\r' -> out.append("\\r");
				case '\t' -> out.append("\\t");
				default -> {
					if (c < 0x20) {
						out.append(String.format("\\u%04x", (int) c));
					}
					else {
						out.append(c);
					}
				}
			}
		}
		out.append('"');
	}

	/** Streaming JSON parser. */
	private static final class Reader {
		private final String text;
		private int pos;

		Reader(String text) {
			this.text = text;
		}

		void end() {
			skip();
			if (pos < text.length()) {
				throw error("trailing content");
			}
		}

		Object value() {
			skip();
			if (pos >= text.length()) {
				throw error("unexpected end of input");
			}
			char c = text.charAt(pos);
			return switch (c) {
				case '{' -> object();
				case '[' -> array();
				case '"' -> string();
				case 't' -> literal("true", Boolean.TRUE);
				case 'f' -> literal("false", Boolean.FALSE);
				case 'n' -> literal("null", null);
				default -> number();
			};
		}

		private Map<String, Object> object() {
			expect('{');
			Map<String, Object> result = new LinkedHashMap<>();
			skip();
			if (peek() == '}') {
				pos++;
				return result;
			}
			while (true) {
				skip();
				String key = string();
				skip();
				expect(':');
				result.put(key, value());
				skip();
				char c = take();
				if (c == '}') {
					return result;
				}
				if (c != ',') {
					throw error("expected ',' or '}'");
				}
			}
		}

		private List<Object> array() {
			expect('[');
			List<Object> result = new ArrayList<>();
			skip();
			if (peek() == ']') {
				pos++;
				return result;
			}
			while (true) {
				result.add(value());
				skip();
				char c = take();
				if (c == ']') {
					return result;
				}
				if (c != ',') {
					throw error("expected ',' or ']'");
				}
			}
		}

		private String string() {
			expect('"');
			StringBuilder out = new StringBuilder();
			while (pos < text.length()) {
				char c = text.charAt(pos++);
				if (c == '"') {
					return out.toString();
				}
				if (c == '\\') {
					if (pos >= text.length()) {
						throw error("truncated escape");
					}
					char e = text.charAt(pos++);
					switch (e) {
						case '"' -> out.append('"');
						case '\\' -> out.append('\\');
						case '/' -> out.append('/');
						case 'b' -> out.append('\b');
						case 'f' -> out.append('\f');
						case 'n' -> out.append('\n');
						case 'r' -> out.append('\r');
						case 't' -> out.append('\t');
						case 'u' -> out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
						default -> throw error("bad escape");
					}
				}
				else {
					out.append(c);
				}
			}
			throw error("unterminated string");
		}

		private Object number() {
			int start = pos;
			if (peek() == '-') {
				pos++;
			}
			while (pos < text.length() && "+-0123456789.eE".indexOf(text.charAt(pos)) >= 0) {
				pos++;
			}
			String raw = text.substring(start, pos);
			try {
				return Integer.parseInt(raw);
			}
			catch (NumberFormatException ignored) {
				// fall through
			}
			try {
				return Long.parseLong(raw);
			}
			catch (NumberFormatException ignored) {
				// fall through
			}
			return Double.parseDouble(raw);
		}

		private Object literal(String word, Object value) {
			if (!text.startsWith(word, pos)) {
				throw error("bad literal");
			}
			pos += word.length();
			return value;
		}

		private void expect(char c) {
			skip();
			if (pos >= text.length() || text.charAt(pos) != c) {
				throw error("expected '" + c + "'");
			}
			pos++;
		}

		private char take() {
			if (pos >= text.length()) {
				throw error("unexpected end of input");
			}
			return text.charAt(pos++);
		}

		private char peek() {
			if (pos >= text.length()) {
				return '\0';
			}
			return text.charAt(pos);
		}

		private void skip() {
			while (pos < text.length() && Character.isWhitespace(text.charAt(pos))) {
				pos++;
			}
		}

		private IllegalArgumentException error(String message) {
			return new IllegalArgumentException("malformed JSON at offset " + pos + ": " + message);
		}
	}
}
