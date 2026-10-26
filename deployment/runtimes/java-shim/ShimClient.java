// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

package gitgrader;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Connects the suite side of the shim to the sandbox side.
 *
 * <p>
 * The sandbox container starts first and creates the socket, so the first
 * connect from the suite container routinely races it: {@link #connect} keeps
 * trying until the connect timeout has passed. Once connected, {@link #call}
 * invokes one exported member of the submission and returns its decoded
 * value, raising {@link ShimError} when the call or the socket fails.
 * Non-serialisable arguments are refused before they are sent, so a suite bug
 * cannot tear the stream.
 */
public final class ShimClient implements AutoCloseable {

	private static final String DEFAULT_SOCKET = "/gitgrader-shim/runner.sock";

	private static final String SOCKET_ENV = "SHIM_SOCKET";

	private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;

	private static final long RETRY_DELAY_MILLIS = 75;

	/**
	 * Ceiling on one call awaiting its correlated response. The runner bounds
	 * the whole round anyway (see ShimmedGradingContainer's call budget), but a
	 * suite-side ceiling keeps a lost reply from hanging a test past any
	 * budget; the node client's deadline works the same way.
	 */
	private static final long CALL_DEADLINE_SECONDS = 30;

	private final SocketChannel channel;

	private final String socketPath;

	private final Map<Integer, PendingCall> pending = new HashMap<>();

	private final Object mutex = new Object();

	private int nextId = 1;

	private ShimClient(String socketPath, int connectTimeoutMs) {
		this.socketPath = socketPath;
		this.channel = connectWithRetry(socketPath, connectTimeoutMs);
		Thread reader = new Thread(this::readLoop, "gitgrader-shim-client");
		reader.setDaemon(true);
		reader.start();
	}

	/**
	 * Connects the suite side of the shim to the sandbox side.
	 * @param socketPath the socket on the shared volume; when blank, the
	 * {@code SHIM_SOCKET} environment variable and then the canonical runner
	 * socket
	 * @param connectTimeoutMs how long connecting may take, in milliseconds;
	 * when not positive, the {@code SHIM_CONNECT_TIMEOUT_MS} environment
	 * variable and then 30 seconds
	 * @return a connected client
	 * @throws ShimError with phase {@code connect-timeout} when the deadline
	 * passes
	 */
	public static ShimClient connect(String socketPath, int connectTimeoutMs) {
		String path = (socketPath == null || socketPath.isBlank())
				? System.getenv().getOrDefault("SHIM_SOCKET", DEFAULT_SOCKET)
				: socketPath;
		int timeout = (connectTimeoutMs > 0) ? connectTimeoutMs
				: Integer.parseInt(System.getenv().getOrDefault("SHIM_CONNECT_TIMEOUT_MS",
						String.valueOf(DEFAULT_CONNECT_TIMEOUT_MS)));
		return new ShimClient(path, timeout);
	}

	/**
	 * Invokes one exported member with shim-carryable arguments on the sandbox
	 * side.
	 * @param method the export to call
	 * @param args arguments the codec can carry
	 * @return the decoded return value
	 * @throws ShimError when the call or the socket fails
	 */
	public Object call(String method, List<Object> args) {
		if (!this.channel.isOpen()) {
			throw new ShimError(Phases.CONNECTION_CLOSED, "The shim connection to " + this.socketPath + " is closed");
		}
		List<Object> wireArgs = new ArrayList<>();
		try {
			for (Object arg : args) {
				wireArgs.add(Serializable.serialize(arg, "shim arguments"));
			}
		}
		catch (Serializable.NonSerializableException error) {
			throw new ShimError(Phases.NON_SERIALIZABLE_ARGS, error.getMessage());
		}
		PendingCall pending = new PendingCall();
		int id;
		synchronized (this.mutex) {
			id = this.nextId;
			this.nextId = id + 1;
			this.pending.put(id, pending);
		}
		Map<String, Object> request = new HashMap<>();
		request.put("id", id);
		request.put("method", method);
		request.put("args", wireArgs);
		try {
			this.channel.write(ByteBuffer.wrap((Json.write(request) + "\n").getBytes(StandardCharsets.UTF_8)));
		}
		catch (IOException error) {
			synchronized (this.mutex) {
				this.pending.remove(id);
			}
			throw new ShimError(Phases.CONNECTION_CLOSED,
					"Could not write the call to '" + method + "' on the shim connection to " + this.socketPath);
		}
		return pending.await();
	}

	@Override
	public void close() {
		try {
			this.channel.close();
		}
		catch (IOException error) {
			// The channel is going away either way; failing the pending calls
			// below is what tells the suite the connection is gone.
		}
		failPending("The shim client was closed");
	}

	private void readLoop() {
		ByteBuffer buffer = ByteBuffer.allocate(65536);
		java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
		try {
			while (this.channel.read(buffer) >= 0) {
				buffer.flip();
				while (buffer.hasRemaining()) {
					byte b = buffer.get();
					if (b == '\n') {
						String request = new String(line.toByteArray(), StandardCharsets.UTF_8);
						line.reset();
						if (!request.isBlank()) {
							dispatch(request);
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
			// Falling out of the loop is the connection dying; the pending
			// calls learn about it below.
		}
		failPending("The shim connection to " + this.socketPath + " closed before all calls had answered");
	}

	private void dispatch(String line) {
		Object message;
		try {
			message = Json.read(line);
		}
		catch (Serializable.DeserializationException error) {
			return;
		}
		if (!(message instanceof Map<?, ?> map)) {
			return;
		}
		if (!(map.get("id") instanceof Integer id)) {
			return;
		}
		PendingCall call;
		synchronized (this.mutex) {
			call = this.pending.remove(id);
		}
		if (call == null) {
			return;
		}
		if (map.containsKey("result")) {
			call.complete(Serializable.deserialize(map.get("result")));
			return;
		}
		if (map.get("error") instanceof Map<?, ?> error) {
			String phase = (error.get("phase") instanceof String declared) ? declared : Phases.BAD_REQUEST;
			String detail = (error.get("message") instanceof String text) ? text : "";
			call.complete(new ShimError(phase, detail));
			return;
		}
		call.complete(new ShimError(Phases.BAD_REQUEST, "The shim response carried neither a result nor an error"));
	}

	private void failPending(String message) {
		Map<Integer, PendingCall> calls;
		synchronized (this.mutex) {
			calls = new HashMap<>(this.pending);
			this.pending.clear();
		}
		for (PendingCall call : calls.values()) {
			call.complete(new ShimError(Phases.CONNECTION_CLOSED, message));
		}
	}

	private static SocketChannel connectWithRetry(String socketPath, int connectTimeoutMs) {
		long deadline = System.currentTimeMillis() + connectTimeoutMs;
		UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
		for (;;) {
			try {
				SocketChannel channel = SocketChannel.open(StandardProtocolFamily.UNIX);
				channel.connect(address);
				return channel;
			}
			catch (IOException error) {
				if (System.currentTimeMillis() >= deadline) {
					throw new ShimError(Phases.CONNECT_TIMEOUT,
							"Could not reach the grading shim at " + socketPath + " within " + connectTimeoutMs
									+ "ms");
				}
				try {
					Thread.sleep(RETRY_DELAY_MILLIS);
				}
				catch (InterruptedException interrupted) {
					Thread.currentThread().interrupt();
					throw new ShimError(Phases.CONNECT_TIMEOUT,
							"Interrupted while waiting for the grading shim at " + socketPath);
				}
			}
		}
	}

	private static final class PendingCall {

		private final BlockingQueue<Object> replies = new LinkedBlockingQueue<>(1);

		void complete(Object result) {
			this.replies.add(result);
		}

		Object await() {
			try {
				Object first = this.replies.poll(CALL_DEADLINE_SECONDS, TimeUnit.SECONDS);
				if (first == null) {
					throw new ShimError(Phases.CONNECTION_CLOSED, "The call was abandoned without an answer");
				}
				if (first instanceof RuntimeException error) {
					throw error;
				}
				return first;
			}
			catch (InterruptedException error) {
				Thread.currentThread().interrupt();
				throw new ShimError(Phases.CONNECTION_CLOSED, "The call was interrupted before it could answer");
			}
		}

	}

}
