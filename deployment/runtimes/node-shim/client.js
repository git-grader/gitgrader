/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import net from 'node:net';

import { PHASES } from './phases.js';
import { assertJsonSerializable } from './serializable.js';

export const DEFAULT_SOCKET = '/gitgrader-shim/runner.sock';
export const DEFAULT_CONNECT_TIMEOUT_MS = 30_000;

const RETRY_DELAY_MS = 75;

/**
 * An error that crossed the shim boundary as a protocol error, or that the
 * client raised on the suite side. {@code phase} is the stable machine-readable
 * reason; {@code message} the detail. The name is set to the phase so test
 * runner output carries the same vocabulary the protocol documents.
 */
export class ShimError extends Error {
	constructor(phase, message) {
		super(message);
		this.name = phase;
		this.phase = phase;
	}
}

/**
 * Connects the suite side of the shim to the sandbox side.
 *
 * The sandbox container starts first and creates the socket, so the first
 * connect from the suite container routinely races it: {@code connectWithRetry}
 * keeps trying until {@code connectTimeoutMs} has passed. Once connected the
 * client returns an async proxy of the solution's exports, where every member
 * access becomes a call that is {@code await}ed by the suite and rejected as a
 * {@link ShimError} when the call or the socket fails. Non-serialisable
 * arguments are refused before they are sent, so a suite bug cannot tear the
 * stream.
 *
 * @param {object} options
 * @param {string} [options.socketPath] the socket on the shared volume
 * @param {number} [options.connectTimeoutMs] how long connecting may take
 * @returns {Promise<{ proxy: object, close: () => void }>} the proxy and a
 * shutdown handle
 */
export async function createShimClient({
	socketPath = process.env.SHIM_SOCKET ?? DEFAULT_SOCKET,
	connectTimeoutMs = Number(process.env.SHIM_CONNECT_TIMEOUT_MS ?? DEFAULT_CONNECT_TIMEOUT_MS)
} = {}) {
	const socket = await connectWithRetry(socketPath, connectTimeoutMs);
	socket.setEncoding('utf8');

	const pending = new Map();
	let nextId = 1;
	let buffer = '';
	let closed = false;

	const failPending = (error) => {
		const entries = [...pending.entries()];
		pending.clear();
		for (const [, entry] of entries) {
			entry.reject(error);
		}
		updateRefCount();
	};

	/**
	 * Keeps the process alive only while a call is in flight. The socket is a
	 * persistent handle, so without this the suite's <code>node --test</code>
	 * process would wait on it forever after the last test and the grading run
	 * would time out instead of reporting a complete test run.
	 */
	const updateRefCount = () => {
		if (pending.size > 0) {
			socket.ref();
		}
		else {
			socket.unref();
		}
	};

	socket.on('data', (chunk) => {
		buffer += chunk;
		let newline;
		while ((newline = buffer.indexOf('\n')) !== -1) {
			const line = buffer.slice(0, newline);
			buffer = buffer.slice(newline + 1);
			if (line.trim() === '') {
				continue;
			}
			let message;
			try {
				message = JSON.parse(line);
			}
			catch {
				continue;
			}
			const entry = message !== null && typeof message === 'object' ? pending.get(message.id) : undefined;
			if (!entry) {
				continue;
			}
			pending.delete(message.id);
			if (Object.prototype.hasOwnProperty.call(message, 'result')) {
				entry.resolve(message.result);
			}
			else if (message.error) {
				entry.reject(new ShimError(message.error.phase ?? PHASES.BAD_REQUEST,
					String(message.error.message ?? '')));
			}
			updateRefCount();
		}
	});

	socket.on('error', () => {
		failPending(new ShimError(PHASES.CONNECTION_CLOSED,
			`The shim at ${socketPath} went away while a call was in flight`));
	});

	socket.on('close', () => {
		closed = true;
		failPending(new ShimError(PHASES.CONNECTION_CLOSED,
			`The shim connection to ${socketPath} closed before all calls had answered`));
	});

	/**
	 * Invokes one exported member with JSON-safe arguments on the sandbox side.
	 * @param {string} method the export to call
	 * @param {unknown[]} args arguments already parsed from the suite's source
	 * @returns {Promise<unknown>} the serialised return value
	 */
	const call = async (method, args) => {
		if (closed || socket.destroyed) {
			throw new ShimError(PHASES.CONNECTION_CLOSED, `The shim connection to ${socketPath} is closed`);
		}
		try {
			assertJsonSerializable(args, 'shim arguments');
		}
		catch (error) {
			throw new ShimError(PHASES.NON_SERIALIZABLE_ARGS, error.message);
		}
		const id = nextId++;
		const promise = new Promise((resolve, reject) => pending.set(id, { resolve, reject }));
		updateRefCount();
		try {
			socket.write(JSON.stringify({ id, method, args }) + '\n');
		}
		catch {
			pending.delete(id);
			throw new ShimError(PHASES.CONNECTION_CLOSED,
				`Could not write the call to '${method}' on the shim connection to ${socketPath}`);
		}
		return promise;
	};

	const proxy = new Proxy({}, {
		get(_target, property) {
			if (typeof property !== 'string') {
				return undefined;
			}
			if (property === 'then') {
				// An accidental `await subject` must not start a remote call for an
				// export that does not exist; the proxy simply is not a thenable.
				return undefined;
			}
			return (...args) => call(property, args);
		}
	});

	return {
		proxy,
		socketPath,
		close() {
			if (closed) {
				return;
			}
			closed = true;
			socket.end();
			failPending(new ShimError(PHASES.CONNECTION_CLOSED, 'The shim client was closed'));
		}
	};
}

/**
 * Connects to a Unix socket, retrying until the deadline, because the sandbox
 * side creates the socket slightly after the runner asks it to start.
 * @param {string} socketPath the Unix socket to connect to
 * @param {number} connectTimeoutMs deadline in milliseconds
 * @returns {Promise<import('node:net').Socket>} the connected socket
 * @throws {ShimError} with phase {@code connect-timeout} when the deadline passes
 */
async function connectWithRetry(socketPath, connectTimeoutMs) {
	const deadline = Date.now() + connectTimeoutMs;
	for (;;) {
		try {
			return await new Promise((resolve, reject) => {
				const socket = net.createConnection(socketPath);
				const onConnect = () => {
					socket.removeListener('error', onError);
					resolve(socket);
				};
				const onError = (error) => {
					socket.removeListener('connect', onConnect);
					socket.destroy();
					reject(error);
				};
				socket.once('connect', onConnect);
				socket.once('error', onError);
			});
		}
		catch {
			if (Date.now() >= deadline) {
				throw new ShimError(PHASES.CONNECT_TIMEOUT,
					`Could not reach the grading shim at ${socketPath} within ${connectTimeoutMs}ms`);
			}
			await new Promise((resolve) => setTimeout(resolve, RETRY_DELAY_MS));
		}
	}
}