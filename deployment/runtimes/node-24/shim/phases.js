/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * The fixed vocabulary of a shim error's {@code phase}. A phase is a stable,
 * machine-readable reason; the message carries the human detail. A stack trace
 * is never transmitted across the socket: it is instructor-only output logged
 * by the suite side.
 */
export const PHASES = Object.freeze({
	/** The request was not a well-formed protocol message. */
	BAD_REQUEST: 'bad-request',
	/** The solution does not export a member with that name. */
	NO_SUCH_MEMBER: 'no-such-member',
	/** The member exists but is not callable. */
	NOT_CALLABLE: 'not-callable',
	/** The arguments violated the JSON value contract. */
	NON_SERIALIZABLE_ARGS: 'non-serializable-args',
	/** The returned value violated the JSON value contract. */
	NON_SERIALIZABLE_RESULT: 'non-serializable-result',
	/** The called solution function threw. */
	INVOCATION_ERROR: 'invocation-error',
	/** The called solution function exceeded the per-call limit. */
	INVOCATION_TIMEOUT: 'invocation-timeout',
	/** The suite side never reached the sandbox side in time. */
	CONNECT_TIMEOUT: 'connect-timeout',
	/** The socket was lost while a call was pending or before any call. */
	CONNECTION_CLOSED: 'connection-closed'
});