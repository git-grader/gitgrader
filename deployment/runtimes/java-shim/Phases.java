/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * The fixed vocabulary of a shim error's {@code phase}. A phase is a stable,
 * machine-readable reason; the message carries the human detail. The stack is
 * never transmitted across the socket: it is instructor-only output logged by
 * the suite side.
 */
public final class Phases {

	/** The request was not a well-formed protocol message. */
	public static final String BAD_REQUEST = "bad-request";
	/** The solution does not export a member with that name. */
	public static final String NO_SUCH_MEMBER = "no-such-member";
	/** The member exists but is not callable. */
	public static final String NOT_CALLABLE = "not-callable";
	/** The arguments violated the JSON value contract. */
	public static final String NON_SERIALIZABLE_ARGS = "non-serializable-args";
	/** The returned value violated the JSON value contract. */
	public static final String NON_SERIALIZABLE_RESULT = "non-serializable-result";
	/** The called solution function threw. */
	public static final String INVOCATION_ERROR = "invocation-error";
	/** The called solution function exceeded the per-call limit. */
	public static final String INVOCATION_TIMEOUT = "invocation-timeout";
	/** The suite side never reached the sandbox side in time. */
	public static final String CONNECT_TIMEOUT = "connect-timeout";
	/** The socket was lost while a call was pending or before any call. */
	public static final String CONNECTION_CLOSED = "connection-closed";

	private Phases() {
	}
}
