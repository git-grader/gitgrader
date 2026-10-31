/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.UnixDomainSocketAddress;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Unix-socket shim client used by the hidden JUnit suite side.
 *
 * <p>
 * Connects to the sandbox's shim socket and invokes the submitted solution's
 * public static methods there, blocking for each answer. The server answers
 * calls concurrently and possibly out of order, so responses are correlated by
 * id, never by stream order.
 *
 * <p>
 * The class is deliberately dependency-free: the suite compiles it against a
 * bare JDK, so it sits comfortably on the JUnit runtime classpath.
 */
public class ShimClient {

	private static final String DEFAULT_SOCKET = "/gitgrader-shim/runner.sock";
	private static final int DEFAULT_CONNECT_TIMEOUT_MS = 30_000;
	private static final long RETRY_DELAY_MS = 75;

	private final UnixDomainSocketAddress address;
	private final int connectTimeoutMs;
	private SocketChannel channel;
	private final Map<Long, Pending> pending = new ConcurrentHashMap<>();
	private long nextId = 1;

	/** An in-flight call waiting for its correlated response. */
	private static final class Pending {
		private final CountDownLatch done = new CountDownLatch(1);
		private volatile Object response;
	}

	/**
	 * Connects a shim client to a running sandbox, retrying until the deadline
	 * because the sandbox creates its socket shortly after the runner starts it.
	 */
	public static ShimClient connect() {
		return connect(DEFAULT_SOCKET, DEFAULT_CONNECT_TIMEOUT_MS);
	}

	public static ShimClient connect(String socketPath, int connectTimeoutMs) {
		ShimClient client = new ShimClient(socketPath, connectTimeoutMs);
		client.open();
		return client;
	}

	private ShimClient(String socketPath, int connectTimeoutMs) {
		this.address = UnixDomainSocketAddress.of(socketPath);
		this.connectTimeoutMs = connectTimeoutMs;
	}

	private void open() {
		Instant deadline = Instant.now().plusMillis(connectTimeoutMs);
		for (;;) {
			try {
				SocketChannel candidate = SocketChannel.open(address);
				this.channel = candidate;
				startReader();
				return;
			}
			catch (java.io.IOException error) {
				if (!Instant.now().isBefore(deadline)) {
					throw new ShimException(Phases.CONNECT_TIMEOUT,
							"Could not reach the grading shim at " + address + " within " + connectTimeoutMs + "ms");
				}
				sleep(RETRY_DELAY_MS);
			}
		}
	}

	private void startReader() {
		Thread reader = new Thread(this::readLoop);
		reader.setDaemon(true);
		reader.setName("gitgrader-shim-client-reader");
		reader.start();
	}

	private void readLoop() {
		try (BufferedReader reader = new BufferedReader(
				new InputStreamReader(java.nio.channels.Channels.newInputStream(channel), StandardCharsets.UTF_8))) {
			String line;
			while ((line = reader.readLine()) != null) {
				if (line.isBlank()) {
					continue;
				}
				frame(line);
			}
		}
		catch (java.io.IOException | RuntimeException ignored) {
			// The socket went away; any calls still waiting fail below through
			// their explicit per-call timeout rather than hanging on the latch.
		}
		finally {
			failAll(new ShimException(Phases.CONNECTION_CLOSED,
					"The shim connection closed before all calls had answered"));
		}
	}

	private void frame(String line) {
		Object message = Json.parse(line);
		if (!(message instanceof Map<?, ?> map) || !(map.get("id") instanceof Number number)) {
			return;
		}
		long id = number.longValue();
		Pending entry = pending.remove(id);
		if (entry == null) {
			return;
		}
		if (map.get("result") != null || map.containsKey("result")) {
			entry.response = Serializable.decode(map.get("result"));
		}
		else if (map.get("error") instanceof Map<?, ?> rawError) {
			@SuppressWarnings("unchecked")
			Map<String, Object> error = (Map<String, Object>) rawError;
			String phase = String.valueOf(error.getOrDefault("phase", Phases.BAD_REQUEST));
			entry.response = new ShimException(phase, String.valueOf(error.getOrDefault("message", "")));
		}
		else {
			entry.response = new ShimException(Phases.BAD_REQUEST, "the reply carried neither result nor error");
		}
		entry.done.countDown();
	}

	private void failAll(ShimException error) {
		for (Pending entry : pending.values()) {
			entry.response = error;
			entry.done.countDown();
		}
		pending.clear();
	}

	/**
	 * Invokes one exported member with shim-carryable arguments on the sandbox
	 * side and returns the decoded value.
	 */
	public Object call(String method, Object... args) {
		Object wireArgs = Serializable.encode(List.of(args));
		long id;
		synchronized (this) {
			id = nextId++;
		}
		Pending entry = new Pending();
		pending.put(id, entry);
		try {
			Map<String, Object> request = new java.util.LinkedHashMap<>();
			request.put("id", id);
			request.put("method", method);
			request.put("args", wireArgs);
			String line = Json.write(request) + "\n";
			OutputStream out = java.nio.channels.Channels.newOutputStream(channel);
			synchronized (out) {
				out.write(line.getBytes(StandardCharsets.UTF_8));
				out.flush();
			}
		}
		catch (java.io.IOException error) {
			pending.remove(id);
			throw new ShimException(Phases.CONNECTION_CLOSED, "could not write the call: " + error.getMessage());
		}
		try {
			if (!entry.done.await(60, TimeUnit.SECONDS)) {
				pending.remove(id);
				throw new ShimException(Phases.INVOCATION_TIMEOUT,
						"the call never returned a reply within the suite's waiting window");
			}
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
			throw new ShimException(Phases.CONNECTION_CLOSED, "interrupted while waiting for the reply");
		}
		Object response = entry.response;
		if (response instanceof ShimException error) {
			throw error;
		}
		return response;
	}

	/** Shuts the client down; pending calls fail as closed. */
	public void close() {
		failAll(new ShimException(Phases.CONNECTION_CLOSED, "the shim client was closed"));
		try {
			channel.close();
		}
		catch (java.io.IOException ignored) {
			// already gone
		}
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		}
		catch (InterruptedException error) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * An error that crossed the shim boundary as a protocol error. {@code phase}
	 * is the stable machine-readable reason; {@code message} the detail.
	 */
	public static final class ShimException extends RuntimeException {
		private static final long serialVersionUID = 1L;
		private final String phase;

		ShimException(String phase, String message) {
			super(message);
			this.phase = phase;
		}

		public String phase() {
			return phase;
		}
	}
}
