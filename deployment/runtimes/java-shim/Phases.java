// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

package gitgrader;

/**
 * The fixed vocabulary of a shim error's phase.
 *
 * <p>
 * A phase is a stable, machine-readable reason; the message carries the human
 * detail. A stack trace is never transmitted across the socket: it is
 * instructor-only output logged by the suite side.
 */
public final class Phases {

	private Phases() {
	}

	public static final String BAD_REQUEST = "bad-request";

	public static final String NO_SUCH_MEMBER = "no-such-member";

	public static final String NOT_CALLABLE = "not-callable";

	public static final String NON_SERIALIZABLE_ARGS = "non-serializable-args";

	public static final String NON_SERIALIZABLE_RESULT = "non-serializable-result";

	public static final String INVOCATION_ERROR = "invocation-error";

	public static final String INVOCATION_TIMEOUT = "invocation-timeout";

	public static final String CONNECT_TIMEOUT = "connect-timeout";

	public static final String CONNECTION_CLOSED = "connection-closed";

}
