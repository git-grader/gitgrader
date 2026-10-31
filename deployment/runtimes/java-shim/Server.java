/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.channels.Channels;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unix-socket shim server that exposes a submitted Java solution's public
 * static methods to the hidden suite side.
 *
 * <p>
 * The sandbox runs this class as its grading process: it compiles the submitted
 * source with the JDK's in-process compiler into a temporary directory, loads
 * the classes, and serves every public static method as a callable export over
 * the NDJSON wire. Each call runs on its own thread and may take up to the
 * per-call ceiling; a call that exceeds the ceiling is reported and its worker
 * abandoned, so one hung solution method cannot wedge the rest of the suite.
 */
public final class Server {

	private static final String DEFAULT_SOCKET = "/gitgrader-shim/runner.sock";
	private static final String DEFAULT_SOLUTION = "/workspace/src/Solution.java";
	private static final int DEFAULT_CALL_TIMEOUT_MS = 30_000;

	private final Path socketPath;
	private final Path classesDir;
	private final Map<String, Method> exports;
	private final int callTimeoutMs;
	private ServerSocketChannel listener;
	private volatile boolean stopping;

	private Server(Path socketPath, Path classesDir, Map<String, Method> exports, int callTimeoutMs) {
		this.socketPath = socketPath;
		this.classesDir = classesDir;
		this.exports = exports;
		this.callTimeoutMs = callTimeoutMs;
	}

	public static Server start() throws IOException {
		String socketValue = System.getenv("SHIM_SOCKET");
		Path socketPath = Path.of(socketValue == null || socketValue.isBlank() ? DEFAULT_SOCKET : socketValue);
		String solutionValue = System.getenv("SOLUTION_PATH");
		Path solutionPath = Path.of(solutionValue == null || solutionValue.isBlank() ? DEFAULT_SOLUTION : solutionValue);
		int callTimeoutMs = DEFAULT_CALL_TIMEOUT_MS;
		String timeoutValue = System.getenv("SHIM_CALL_TIMEOUT_MS");
		if (timeoutValue != null && !timeoutValue.isBlank()) {
			callTimeoutMs = Integer.parseInt(timeoutValue);
		}
		return start(socketPath, solutionPath, callTimeoutMs);
	}

	/**
	 * Starts a server (used by the self-test and the container alike).
	 * @param socketPath where to listen on the shared volume
	 * @param solutionPath the submitted source
	 * @param callTimeoutMs ceiling on one call
	 * @return the running server
	 * @throws IOException when the solution cannot compile or bind
	 */
	public static Server start(Path socketPath, Path solutionPath, int callTimeoutMs) throws IOException {
		Path classesDir = Files.createTempDirectory("gitgrader-shim-classes-");
		compile(solutionPath, classesDir);
		Map<String, Method> exports = discover(solutionPath, classesDir);
		Server server = new Server(socketPath, classesDir, exports, callTimeoutMs);
		server.bind();
		return server;
	}

	private void bind() throws IOException {
		Files.createDirectories(socketPath.getParent());
		Files.deleteIfExists(socketPath);
		listener = ServerSocketChannel.open(java.net.StandardProtocolFamily.UNIX);
		listener.bind(java.net.UnixDomainSocketAddress.of(socketPath.toString()));
		Thread accept = new Thread(this::acceptLoop);
		accept.setDaemon(true);
		accept.setName("gitgrader-shim-accept");
		accept.start();
		print("shim server listening on " + socketPath + " serving " + exports.size() + " exports");
	}

	private void acceptLoop() {
		while (!stopping) {
			try {
				SocketChannel connection = listener.accept();
				Thread worker = new Thread(() -> handle(connection), "gitgrader-shim-connection");
				worker.setDaemon(true);
				worker.start();
			}
			catch (IOException ignored) {
				if (stopping) {
					break;
				}
			}
		}
	}

	private void handle(SocketChannel connection) {
		final Object writeLock = new Object();
		try (connection) {
			StringBuilder buffer = new StringBuilder();
			java.io.Reader reader = new java.io.InputStreamReader(Channels.newInputStream(connection), StandardCharsets.UTF_8);
			char[] chunk = new char[4096];
			int read;
			while ((read = reader.read(chunk)) != -1) {
				buffer.append(chunk, 0, read);
				String text = buffer.toString();
				int newline;
				int consumed = 0;
				while ((newline = text.indexOf('\n', consumed)) != -1) {
					String line = text.substring(consumed, newline).strip();
					consumed = newline + 1;
					if (!line.isEmpty()) {
						serve(line, connection, writeLock);
					}
				}
				buffer.setLength(0);
				buffer.append(text.substring(consumed));
			}
		}
		catch (IOException ignored) {
			// tear-down is the runner's job, not the server's
		}
	}

	/** Writes one NDJSON response line, guarded against interleaving threads. */
	private void write(SocketChannel connection, Object writeLock, Object message) {
		try {
			synchronized (writeLock) {
				java.io.Writer out = new java.io.OutputStreamWriter(
						Channels.newOutputStream(connection), StandardCharsets.UTF_8);
				out.write(Json.write(message));
				out.write('\n');
				out.flush();
			}
		}
		catch (IOException ignored) {
			// The suite side went away mid-response; the connection teardown is
			// the runner's job, not the server's.
		}
	}

	/** Parses one request line and answers it, exactly once, possibly asynchronously. */
	private void serve(String line, SocketChannel connection, Object writeLock) {
		Object request;
		try {
			request = Json.parse(line);
		}
		catch (IllegalArgumentException malformed) {
			write(connection, writeLock, Map.of("error", error(Phases.BAD_REQUEST, "Malformed JSON on the shim socket")));
			return;
		}
		if (!(request instanceof Map<?, ?> map) || !(map.get("id") instanceof Number)
				|| !(map.get("method") instanceof String) || !(map.get("args") instanceof List<?>)) {
			write(connection, writeLock,
					Map.of("error", error(Phases.BAD_REQUEST, "A shim request needs a numeric id, a string method and an array of args")));
			return;
		}
		Number idValue = (Number) map.get("id");
		long id = idValue.longValue();
		String method = (String) map.get("method");
		List<?> wireArgs = (List<?>) map.get("args");

		// Guarantee exactly one response per request even across a timeout race.
		java.util.concurrent.atomic.AtomicBoolean answered = new java.util.concurrent.atomic.AtomicBoolean();
		Thread worker = new Thread(() -> invoke(method, wireArgs, id, connection, writeLock, answered),
				"invoke-" + method);
		worker.setDaemon(true);
		worker.start();
		// The per-call ceiling is enforced from a small watching thread: once it
		// passes with the worker still running, the timeout answer is sent and the
		// worker abandoned (it dies with the container).
		Thread watchdog = new Thread(() -> {
			try {
				worker.join(callTimeoutMs);
				if (worker.isAlive()) {
					respond(connection, writeLock, answered, id, err(id, Phases.INVOCATION_TIMEOUT,
							"'" + method + "' exceeded the " + callTimeoutMs + "ms per-call shim limit"));
				}
			}
			catch (InterruptedException interrupted) {
				Thread.currentThread().interrupt();
			}
		}, "timeout-" + method);
		watchdog.setDaemon(true);
		watchdog.start();
	}

	private void invoke(String method, List<?> wireArgs, long id, SocketChannel connection,
			Object writeLock, java.util.concurrent.atomic.AtomicBoolean answered) {
		Method target = exports.get(method);
		if (target == null) {
			respond(connection, writeLock, answered, id,
					err(id, Phases.NO_SUCH_MEMBER, "The submitted solution does not export '" + method + "'"));
			return;
		}
		if (!Modifier.isStatic(target.getModifiers())) {
			respond(connection, writeLock, answered, id,
					err(id, Phases.NOT_CALLABLE, "'" + method + "' is not callable by the suite"));
			return;
		}
		List<Object> args;
		try {
			List<?> decoded = (List<?>) Serializable.decode(wireArgs);
			args = arguments(target, decoded);
		}
		catch (IllegalArgumentException problem) {
			respond(connection, writeLock, answered, id,
					err(id, Phases.NON_SERIALIZABLE_ARGS, problem.getMessage()));
			return;
		}
		Object value;
		try {
			value = target.invoke(null, args.toArray());
		}
		catch (InvocationTargetException thrown) {
			Throwable cause = thrown.getCause();
			String message = cause == null ? String.valueOf(thrown) : describeThrown(cause);
			respond(connection, writeLock, answered, id, err(id, Phases.INVOCATION_ERROR, message));
			return;
		}
		catch (ReflectiveOperationException problem) {
			respond(connection, writeLock, answered, id, err(id, Phases.INVOCATION_ERROR, problem.getMessage()));
			return;
		}
		Object wireResult;
		try {
			wireResult = Serializable.encode(value);
		}
		catch (IllegalArgumentException problem) {
			respond(connection, writeLock, answered, id,
					err(id, Phases.NON_SERIALIZABLE_RESULT, problem.getMessage()));
			return;
		}
		respond(connection, writeLock, answered, id, resultResponse(id, wireResult));
	}

	private void respond(SocketChannel connection, Object writeLock,
			java.util.concurrent.atomic.AtomicBoolean answered, long id, Object message) {
		if (answered.compareAndSet(false, true)) {
			write(connection, writeLock, message);
		}
	}

	private static Map<String, Object> error(String phase, String message) {
		return Map.of("phase", phase, "message", message);
	}

	private static Map<String, Object> err(long id, String phase, String message) {
		return Map.of("id", id, "error", error(phase, message));
	}

	private static Map<String, Object> resultResponse(long id, Object wireResult) {
		return Map.of("id", id, "result", wireResult);
	}

	/**
	 * Maps wire arguments onto the declared parameter list of a method, converting
	 * JSON arrays to the method's primitives where needed.
	 * @param target the method to invoke
	 * @param wireArgs decoded wire value-producing JSON array of args
	 * @return Java arguments, in declaration order
	 * @throws IllegalArgumentException when counts differ or a value cannot convert
	 */
	private static List<Object> arguments(Method target, List<?> wireArgs) {
		Class<?>[] parameterTypes = target.getParameterTypes();
		if (wireArgs.size() != parameterTypes.length) {
			throw new IllegalArgumentException("'" + target.getName() + "' takes " + parameterTypes.length
					+ " arguments but the suite supplied " + wireArgs.size());
		}
		List<Object> args = new ArrayList<>(parameterTypes.length);
		for (int i = 0; i < parameterTypes.length; i++) {
			args.add(coerce(parameterTypes[i], wireArgs.get(i)));
		}
		return args;
	}

	/** Converts one decoded wire value to the declared parameter type. */
	private static Object coerce(Class<?> type, Object value) {
		if (type == String.class) {
			if (value == null || value instanceof String) {
				return value;
			}
			throw typeError(value, "String");
		}
		if (type == int.class || type == Integer.class) {
			return integer(value, type, Number::intValue);
		}
		if (type == long.class || type == Long.class) {
			return longValue(value, type);
		}
		if (type == double.class || type == Double.class) {
			return doubleValue(value, type);
		}
		if (type == boolean.class || type == Boolean.class) {
			if (value instanceof Boolean) {
				return value;
			}
			throw typeError(value, "boolean");
		}
		if (type == byte[].class) {
			if (value == null || value instanceof byte[]) {
				return value;
			}
			throw typeError(value, "byte[]");
		}
		if (type == int[].class) {
			return intArray(value);
		}
		if (type == String[].class) {
			return stringArray(value);
		}
		throw typeError(value, type.getName());
	}

	private static Object integer(Object value, Class<?> type, java.util.function.Function<Number, Object> boxer) {
		if (value instanceof Number number) {
			return boxer.apply(number);
		}
		throw typeError(value, type.getName());
	}

	private static Object longValue(Object value, Class<?> type) {
		return integer(value, type, Number::longValue);
	}

	private static Object doubleValue(Object value, Class<?> type) {
		if (value instanceof Number number) {
			return number.doubleValue();
		}
		throw typeError(value, type.getName());
	}

	private static int[] intArray(Object value) {
		if (!(value instanceof List<?> list)) {
			throw typeError(value, "int[]");
		}
		int[] result = new int[list.size()];
		for (int i = 0; i < list.size(); i++) {
			result[i] = (Integer) integer(list.get(i), int.class, Number::intValue);
		}
		return result;
	}

	private static String[] stringArray(Object value) {
		if (!(value instanceof List<?> list)) {
			throw typeError(value, "String[]");
		}
		String[] result = new String[list.size()];
		for (int i = 0; i < list.size(); i++) {
			Object item = list.get(i);
			if (item == null || item instanceof String) {
				result[i] = (String) item;
			}
			else {
				throw typeError(item, "String");
			}
		}
		return result;
	}

	private static IllegalArgumentException typeError(Object value, String expected) {
		return new IllegalArgumentException(
				"a value of type " + (value == null ? "null" : value.getClass().getSimpleName())
						+ " cannot convert to " + expected);
	}

	/** Deliberately ships no stack: it names paths and line numbers of the hidden call. */
	private static String describeThrown(Throwable thrown) {
		String message = thrown.getMessage();
		return message == null || message.isBlank() ? thrown.getClass().getSimpleName() : message;
	}

	/** Compiles the submitted source into {@code classesDir} with the in-process javac. */
	private static void compile(Path solutionPath, Path classesDir) throws IOException {
		java.io.PrintStream sink = new java.io.PrintStream(java.io.OutputStream.nullOutputStream());
		int result = javax.tools.ToolProvider.getSystemJavaCompiler()
			.run(null, sink, sink,
					"-d", classesDir.toString(), solutionPath.toString());
		if (result != 0) {
			throw new IOException("the submitted solution did not compile (javac exit " + result + ")");
		}
	}

	/** Loads the compiled solution and indexes every public static method by name. */
	private static Map<String, Method> discover(Path solutionPath, Path classesDir) throws IOException {
		try (java.net.URLClassLoader loader = new java.net.URLClassLoader(
				new java.net.URL[] { classesDir.toUri().toURL() }, Server.class.getClassLoader())) {
			String className = classNameOf(solutionPath);
			Map<String, Method> exports = new java.util.TreeMap<>();
			try {
				Class<?> type = Class.forName(className, true, loader);
				for (Method method : type.getDeclaredMethods()) {
					if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers())) {
						exports.put(method.getName(), method);
					}
				}
			}
			catch (ClassNotFoundException notFound) {
				throw new IOException("the compiled solution class " + className + " was not found: "
						+ notFound.getMessage());
			}
			return exports;
		}
	}

	/** Derives the fully-qualified class name from the solution's source path. */
	private static String classNameOf(Path solutionPath) throws IOException {
		String source = Files.readString(solutionPath);
		String packageName = "";
		for (String line : source.split("\\R")) {
			String trimmed = line.strip();
			if (trimmed.startsWith("package ") && trimmed.endsWith(";")) {
				packageName = trimmed.substring("package ".length(), trimmed.length() - 1).strip();
				break;
			}
		}
		String fileName = solutionPath.getFileName().toString();
		String simpleName = fileName.substring(0, fileName.length() - ".java".length());
		return packageName.isBlank() ? simpleName : packageName + "." + simpleName;
	}

	/** Diagnostic sink: the runner reads container stderr, never this as a report. */
	private static void print(String line) {
		System.err.println(line);
	}

	/** Stops accepting, closes the socket and removes its file. */
	void close() throws IOException {
		stopping = true;
		if (listener != null) {
			listener.close();
		}
		Files.deleteIfExists(socketPath);
	}

	public static void main(String[] args) throws Exception {
		Server server = Server.start();
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			try {
				server.close();
			}
			catch (IOException ignored) {
			}
		}));
		// The accept/worker threads are daemons; keep the process alive until the
		// runner tears the container down.
		Thread.currentThread().join(Long.MAX_VALUE);
	}
}

