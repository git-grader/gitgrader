// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

package gitgrader;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.ProtocolFamily;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * Starts a shim server in front of the submitted solution.
 *
 * <p>
 * The server loads the single public class found under the submitted source
 * directory (or the file named by {@code SOLUTION_PATH}) and serves each of
 * its static public methods to the suite side over a Unix socket: one NDJSON
 * request per call, one NDJSON response per request, correlated by id. Values
 * are carried in both directions through {@link Serializable}. A call may
 * finish in whatever time it takes up to the per-call ceiling; a call that
 * exceeds it fails with {@code invocation-timeout} so the suite is not stuck
 * on a solution that never returns.
 */
public final class ShimServer {

	/** Socket inside the shared mount; the suite's client dials here. */
	private static final String DEFAULT_SOCKET = "/gitgrader-shim/runner.sock";

	/** Default location of the submitted source inside the sandbox. */
	private static final String DEFAULT_SOLUTION_PATH = "/workspace/src";

	private static final int DEFAULT_CALL_TIMEOUT_MS = 30_000;

	private final Path socketPath;

	private final Class<?> solution;

	private final long callTimeoutMillis;

	private ServerSocketChannel channel;

	private ExecutorService executor;

	private ShimServer(Path socketPath, Class<?> solution, long callTimeoutMillis) {
		this.socketPath = socketPath;
		this.solution = solution;
		this.callTimeoutMillis = callTimeoutMillis;
	}

	/**
	 * Raised before the server listens, e.g. because the submitted class could
	 * not be loaded.
	 */
	public static final class ShimStartupError extends RuntimeException {

		ShimStartupError(String message) {
			super(message);
		}

		ShimStartupError(String message, Throwable cause) {
			super(message, cause);
		}

	}

	/**
	 * Starts a shim server in front of the submitted solution.
	 * @param socketPath where to listen; must be a path on the volume shared
	 * with the suite side
	 * @param solutionPath the submitted source tree (a directory holding
	 * {@code .java} files, or one such file) to load
	 * @param callTimeoutMs ceiling on one call, in milliseconds
	 * @return a started server
	 * @throws ShimStartupError when no source module can be chosen or loaded
	 */
	public static ShimServer start(Path socketPath, Path solutionPath, int callTimeoutMs) {
		Class<?> solution = loadSolution(solutionPath);
		ShimServer server = new ShimServer(socketPath, solution, callTimeoutMs);
		server.listen();
		return server;
	}

	/**
	 * Resolves which submitted source the server should load.
	 *
	 * <p>
	 * An explicit {@code SOLUTION_PATH} wins when the named file exists.
	 * Otherwise the server looks for the single {@code .java} file in the
	 * workspace's src/ directory, so assignments work without a per-assignment
	 * path wired into the grader. When the tree holds several sources the
	 * server refuses to guess and asks for an explicit SOLUTION_PATH.
	 * @param explicit from the SOLUTION_PATH environment
	 * @param fallback the runtime's default solution path
	 * @return the source path whose public class is the submission
	 */
	static Path resolveSolutionPath(String explicit, Path fallback) {
		if (explicit != null && !explicit.isBlank() && Files.isRegularFile(Path.of(explicit))) {
			return Path.of(explicit);
		}
		Path source = (explicit == null || explicit.isBlank()) ? fallback : Path.of(explicit);
		if (source.toString().endsWith(".java") && Files.isRegularFile(source)) {
			return source;
		}
		List<Path> modules = discoverSolutionModules(source);
		if (modules.size() == 1) {
			return modules.get(0);
		}
		if (modules.isEmpty()) {
			throw new ShimStartupError("Could not find the submitted solution: nothing named " + source
					+ " exists and " + source + " holds no module.");
		}
		throw new ShimStartupError("Could not choose the submitted solution: " + source + " holds " + modules.size()
				+ " modules (" + modules + "). Set SOLUTION_PATH to the one to test.");
	}

	private static List<Path> discoverSolutionModules(Path srcDir) {
		if (!Files.isDirectory(srcDir)) {
			return List.of();
		}
		try (var files = Files.list(srcDir)) {
			return files.filter(Files::isRegularFile)
				.filter((path) -> path.toString().endsWith(".java"))
				.sorted()
				.toList();
		}
		catch (IOException error) {
			return List.of();
		}
	}

	private static Class<?> loadSolution(Path solutionPath) {
		// The runtime image compiles the workspace before the server starts;
		// the compiled classes land beside the sources, so the class loader
		// only has to see the parent directory.
		Path parent = solutionPath.toAbsolutePath().getParent();
		java.net.URL classUrl;
		try {
			classUrl = parent.toUri().toURL();
		}
		catch (java.net.MalformedURLException error) {
			throw new ShimStartupError("Could not read the compiled solution under " + parent, error);
		}
		ClassLoader loader = new java.net.URLClassLoader(new java.net.URL[] { classUrl },
				ShimServer.class.getClassLoader());
		List<Class<?>> publics = new ArrayList<>();
		for (Path source : discoverSolutionModules(parent)) {
			String name = source.getFileName().toString().replaceFirst("\\.java$", "");
			try {
				Class<?> type = Class.forName(name, false, loader);
				if (java.lang.reflect.Modifier.isPublic(type.getModifiers())
						&& !java.lang.reflect.Modifier.isAbstract(type.getModifiers())) {
					publics.add(type);
				}
			}
			catch (ClassNotFoundException error) {
				throw new ShimStartupError("Could not load the submitted class " + name + " from " + source, error);
			}
		}
		if (publics.size() == 1) {
			return publics.get(0);
		}
		if (publics.isEmpty()) {
			throw new ShimStartupError(
					"Could not find a public class under " + parent + "; compile the submission first");
		}
		throw new ShimStartupError("Could not choose the submitted class: " + parent + " holds " + publics.size()
				+ " public classes. Set SOLUTION_PATH to the one to test.");
	}

	private void listen() {
		try {
			Files.createDirectories(this.socketPath.getParent());
			Files.deleteIfExists(this.socketPath);
			this.channel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
			this.channel.bind(UnixDomainSocketAddress.of(this.socketPath));
			// The suite container never shares the sandbox's uid, and a Unix
			// socket connect needs write access to the socket file itself, so
			// the socket must be group/world writable the way the socket
			// directory is (see ShimmedGradingContainer).
			try {
				java.nio.file.Files.setPosixFilePermissions(this.socketPath,
						java.nio.file.attribute.PosixFilePermissions.fromString("rw-rw-rw-"));
			}
			catch (UnsupportedOperationException error) {
				System.err.println("Could not set the socket permissions; the suite may not reach the socket");
			}
			this.executor = Executors.newCachedThreadPool((runnable) -> {
				Thread thread = new Thread(runnable, "gitgrader-shim-server");
				thread.setDaemon(true);
				return thread;
			});
			Thread acceptor = new Thread(this::acceptLoop, "gitgrader-shim-acceptor");
			acceptor.setDaemon(true);
			acceptor.start();
			System.err.println("shim server listening on " + this.socketPath + " (solution " + this.solution.getName()
					+ ")");
		}
		catch (IOException error) {
			throw new ShimStartupError("Could not listen on " + this.socketPath, error);
		}
	}

	private void acceptLoop() {
		while (this.channel != null && this.channel.isOpen()) {
			try {
				SocketChannel connection = this.channel.accept();
				if (connection != null) {
					this.executor.submit(() -> serveConnection(connection));
				}
			}
			catch (IOException error) {
				// The channel is closing; the acceptor thread ends with it.
				return;
			}
		}
	}

	private void serveConnection(SocketChannel connection) {
		java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
		ByteBuffer buffer = ByteBuffer.allocate(65536);
		try (connection) {
			while (connection.isConnected() && connection.read(buffer) >= 0) {
				buffer.flip();
				while (buffer.hasRemaining()) {
					byte b = buffer.get();
					if (b == '\n') {
						// The line buffer is the request; answer it and continue.
						String request = new String(line.toByteArray(), StandardCharsets.UTF_8);
						line.reset();
						if (!request.isBlank()) {
							serveLine(connection, request);
						}
					}
					else {
						line.write(b);
					}
				}
				buffer.compact();
			}
		}
		catch (IOException error) {
			// The suite side went away mid-call; the connection teardown that
			// follows is the runner's job, not the server's.
		}
	}

	/**
	 * Answers one request line without blocking later ones, so calls from the
	 * suite side may run concurrently and interleave. The suite side
	 * correlates responses by id, never by arrival order.
	 * @param connection the connection to answer on
	 * @param line one newline-delimited JSON request
	 */
	private void serveLine(SocketChannel connection, String line) {
		Object request;
		try {
			request = Json.read(line);
		}
		catch (RuntimeException error) {
			write(connection, Map.of("error", Map.of("phase", Phases.BAD_REQUEST,
					"message", "Malformed JSON on the shim socket")));
			return;
		}
		if (!(request instanceof Map<?, ?> map) || !(map.get("id") instanceof Integer id)
				|| !(map.get("method") instanceof String method) || !(map.get("args") instanceof List<?> wireArgs)) {
			write(connection, Map.of("error", Map.of("phase", Phases.BAD_REQUEST,
					"message", "A shim request needs a numeric id, a string method and an array of args")));
			return;
		}
		List<Object> args = new ArrayList<>();
		try {
			for (Object wireArg : wireArgs) {
				args.add(Serializable.deserialize(wireArg));
			}
		}
		catch (Serializable.DeserializationException error) {
			write(connection,
					Map.of("id", id, "error",
							Map.of("phase", Phases.NON_SERIALIZABLE_ARGS, "message",
									String.valueOf(error.getMessage()))));
			return;
		}
		Method member = findMethod(method);
		if (member == null) {
			write(connection, Map.of("id", id, "error", Map.of("phase", Phases.NO_SUCH_MEMBER,
					"message", "The submitted solution does not export '" + method + "'")));
			return;
		}
		Object result;
		try {
			result = invokeWithTimeout(member, args, this.callTimeoutMillis);
		}
		catch (java.util.concurrent.TimeoutException error) {
			write(connection,
					Map.of("id", id, "error",
							Map.of("phase", Phases.INVOCATION_TIMEOUT, "message",
									"'" + method + "' exceeded the " + this.callTimeoutMillis
											+ "ms per-call shim limit")));
			return;
		}
		catch (java.util.concurrent.ExecutionException error) {
			Throwable thrown = (error.getCause() != null) ? error.getCause() : error;
			write(connection, Map.of("id", id, "error",
					Map.of("phase", Phases.INVOCATION_ERROR, "message", describeThrown(thrown))));
			return;
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			return;
		}
		Object wireResult;
		try {
			wireResult = Serializable.serialize(result, "shim result");
		}
		catch (Serializable.NonSerializableException shimError) {
			write(connection,
					Map.of("id", id, "error",
							Map.of("phase", Phases.NON_SERIALIZABLE_RESULT, "message",
									String.valueOf(shimError.getMessage()))));
			return;
		}
		write(connection, Map.of("id", id, "result", wireResult));
	}

	private void write(SocketChannel connection, Object message) {
		try {
			connection.write(ByteBuffer.wrap((Json.write(message) + "\n").getBytes(StandardCharsets.UTF_8)));
		}
		catch (IOException error) {
			// The suite side went away mid-response; the connection teardown
			// that follows is the runner's job, not the server's.
		}
	}

	/**
	 * Finds the static public method a suite asked for.
	 * @param name the export the suite named
	 * @return the method, or {@code null} when the solution does not export a
	 * static method of that name
	 */
	private Method findMethod(String name) {
		for (Method candidate : this.solution.getMethods()) {
			if (candidate.getName().equals(name) && Modifier.isStatic(candidate.getModifiers())
					&& Modifier.isPublic(candidate.getModifiers())) {
				return candidate;
			}
		}
		return null;
	}

	private Object invokeWithTimeout(Method member, List<Object> args, long timeoutMillis)
			throws java.util.concurrent.TimeoutException, java.util.concurrent.ExecutionException,
			InterruptedException {
		Future<Object> future = this.executor.submit(() -> member.invoke(null, args.toArray()));
		try {
			return future.get(timeoutMillis, TimeUnit.MILLISECONDS);
		}
		catch (java.util.concurrent.TimeoutException error) {
			future.cancel(true);
			throw error;
		}
	}

	/**
	 * Turns whatever a solution threw into the message that reaches the suite.
	 * The stack is deliberately not shipped: it names paths and line numbers
	 * the student must not learn from a hidden call.
	 * @param thrown anything a student method can throw
	 * @return a stable, short description
	 */
	private static String describeThrown(Throwable thrown) {
		String message = thrown.getMessage();
		return (message == null || message.isBlank()) ? thrown.getClass().getSimpleName() : message;
	}

	/**
	 * Stops the server, closing the socket and the worker pool.
	 */
	public void close() {
		try {
			if (this.channel != null && this.channel.isOpen()) {
				this.channel.close();
			}
		}
		catch (IOException error) {
			// The channel is going away either way.
		}
		if (this.executor != null) {
			this.executor.shutdownNow();
		}
		try {
			Files.deleteIfExists(this.socketPath);
		}
		catch (IOException error) {
			// Best effort; the volume is discarded after the run.
		}
	}

	/**
	 * Runs a shim server from the environment: {@code SHIM_SOCKET},
	 * {@code SOLUTION_PATH} and {@code SHIM_CALL_TIMEOUT_MS}.
	 * @param args ignored
	 */
	public static void main(String[] args) {
		String socketPath = System.getenv().getOrDefault("SHIM_SOCKET", DEFAULT_SOCKET);
		String explicit = System.getenv("SOLUTION_PATH");
		Path solutionPath;
		try {
			solutionPath = resolveSolutionPath(explicit, Path.of(DEFAULT_SOLUTION_PATH));
		}
		catch (ShimStartupError error) {
			System.err.println("The grading shim server could not start: " + error.getMessage());
			System.exit(1);
			return;
		}
		int timeout = Integer
			.parseInt(System.getenv().getOrDefault("SHIM_CALL_TIMEOUT_MS", String.valueOf(DEFAULT_CALL_TIMEOUT_MS)));
		try {
			ShimServer server = start(Path.of(socketPath), solutionPath, timeout);
			// The server serves until the runner stops the container; the main
			// thread parks so the daemon workers it spawned keep running.
			Thread.currentThread().join();
			server.close();
		}
		catch (ShimStartupError error) {
			System.err.println("The grading shim server could not start: " + error.getMessage());
			System.exit(1);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		}
	}

}
