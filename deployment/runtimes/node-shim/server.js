/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import net from 'node:net';
import { mkdir, rm } from 'node:fs/promises';
import { existsSync, readdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { PHASES } from './phases.js';
import { serialize, deserialize } from './serializable.js';

export const DEFAULT_SOCKET = '/gitgrader-shim/runner.sock';
export const DEFAULT_SOLUTION_PATH = '/workspace/src/string-utils.js';
export const DEFAULT_CALL_TIMEOUT_MS = 30_000;

/**
 * Lists the submitted modules a workspace offers in its src/ directory.
 * @param {string} srcDir absolute path of the src/ directory to scan
 * @returns {string[]} absolute paths of the .js files directly under it,
 * sorted, or [] when the directory is absent
 */
export function discoverSolutionModules(srcDir) {
	if (!existsSync(srcDir)) return [];
	return readdirSync(srcDir, { withFileTypes: true })
		.filter((entry) => entry.isFile() && entry.name.endsWith('.js'))
		.map((entry) => resolve(srcDir, entry.name))
		.sort();
}

/**
 * Resolves which submitted module the server should load.
 *
 * An explicit {@code SOLUTION_PATH} wins when the named file exists. Otherwise
 * the server looks for the single module in the workspace's src/ directory, so
 * assignments such as the WBE set work without a per-assignment path wired into
 * the grader. When src/ holds several modules the server refuses to guess and
 * asks for an explicit SOLUTION_PATH.
 *
 * @param {string|undefined} explicit from the SOLUTION_PATH environment
 * @param {string} fallback the runtime's default solution path
 * @returns {string} the absolute path of the module to load
 * @throws {ShimStartupError} when no source module can be chosen
 */
export function resolveSolutionPath(explicit, fallback = DEFAULT_SOLUTION_PATH) {
	if (explicit !== undefined && explicit !== '' && existsSync(explicit)) {
		return explicit;
	}
	const srcDir = dirname(explicit ?? fallback);
	const modules = discoverSolutionModules(srcDir);
	if (modules.length === 1) return modules[0];
	if (modules.length === 0) {
		throw new ShimStartupError(
			`Could not find the submitted solution: nothing named ${fallback} exists and ${srcDir} holds no module.`);
	}
	throw new ShimStartupError(
		`Could not choose the submitted solution: ${srcDir} holds ${modules.length} modules ` +
			`(${modules.join(', ')}). Set SOLUTION_PATH to the one to test.`);
}

/**
 * Error thrown before the server listens, e.g. because the submitted module
 * could not be loaded. The process exits with a stack on standard error.
 */
export class ShimStartupError extends Error {
}

/**
 * Starts a shim server in front of the submitted solution.
 *
 * The server loads the module named by {@code solutionPath} and serves each of
 * its exported functions to the suite side over a Unix socket: one NDJSON
 * request per call, one NDJSON response per request, correlated by id. Values
 * are carried in both directions through {@code serialize}/{@code
 * deserialize}. A call may finish
 * in whatever time it takes up to {@code callTimeoutMs}; a call that exceeds
 * it fails with {@code invocation-timeout} so the suite is not stuck on a
 * solution that never returns.
 *
 * @param {object} options
 * @param {string} [options.socketPath] where to listen; must be a path on the
 * volume shared with the suite side
 * @param {string} [options.solutionPath] the submitted module to load
 * @param {number} [options.callTimeoutMs] ceiling on one call
 * @param {(line: string) => void} [options.log] diagnostic sink; exits only as
 * the container's stderr and is never parsed as a report
 * @returns {Promise<{ close: () => Promise<void> }>} handle for shutting the
 * server down, used by local tests
 */
export async function createShimServer({
	socketPath = DEFAULT_SOCKET,
	solutionPath = DEFAULT_SOLUTION_PATH,
	callTimeoutMs = DEFAULT_CALL_TIMEOUT_MS,
	log = () => {}
} = {}) {
	await mkdir(dirname(socketPath), { recursive: true });
	// A socket file left behind by a killed previous run would make listen()
	// fail; a fresh volume in the runner makes this a no-op, tests rely on it.
	await rm(socketPath, { force: true });

	let namespace;
	try {
		namespace = await import(pathToFileURL(solutionPath).href);
	}
	catch (error) {
		throw new ShimStartupError(`Could not load the submitted solution from ${solutionPath}: ${error.message}`);
	}

	const connections = new Set();
	const server = net.createServer((socket) => {
		connections.add(socket);
		socket.on('close', () => connections.delete(socket));
		socket.setEncoding('utf8');
		let buffer = '';
		socket.on('data', (chunk) => {
			buffer += chunk;
			let newline;
			while ((newline = buffer.indexOf('\n')) !== -1) {
				const line = buffer.slice(0, newline);
				buffer = buffer.slice(newline + 1);
				if (line.trim() !== '') {
					serve(socket, line, namespace, callTimeoutMs);
				}
			}
		});
	});

	await new Promise((resolveListen, rejectListen) => {
		server.once('error', rejectListen);
		server.listen(socketPath, resolveListen);
	});
	log(`shim server listening on ${socketPath} (solution ${solutionPath})`);

	let closed = false;
	return {
		async close() {
			if (closed) {
				return;
			}
			closed = true;
			for (const socket of connections) {
				socket.destroy();
			}
			if (server.listening) {
				await new Promise((resolveClose) => server.close(() => resolveClose()));
			}
			await rm(socketPath, { force: true });
		}
	};
}

/**
 * Answers one request line without blocking later ones, so calls from the
 * suite side may run concurrently and interleave. The suite side correlates
 * responses by id, never by arrival order.
 * @param {import('node:net').Socket} socket the connection to answer on
 * @param {string} line one newline-delimited JSON request
 * @param {object} namespace the loaded solution module namespace
 * @param {number} callTimeoutMs the per-call ceiling
 */
function serve(socket, line, namespace, callTimeoutMs) {
	let request;
	try {
		request = JSON.parse(line);
	}
	catch {
		write(socket, { error: phaseError(PHASES.BAD_REQUEST, 'Malformed JSON on the shim socket') });
		return;
	}
	if (typeof request?.id !== 'number' || typeof request?.method !== 'string' || !Array.isArray(request?.args)) {
		write(socket, { error: phaseError(PHASES.BAD_REQUEST, 'A shim request needs a numeric id, a string method and an array of args') });
		return;
	}
	const { id, method, args: wireArgs } = request;

	void (async () => {
		let args;
		try {
			args = deserialize(wireArgs);
		}
		catch (error) {
			write(socket, { id, error: phaseError(PHASES.NON_SERIALIZABLE_ARGS, error.message) });
			return;
		}

		if (!Object.hasOwn(namespace, method)) {
			write(socket, {
				id,
				error: phaseError(PHASES.NO_SUCH_MEMBER, `The submitted solution does not export '${method}'`)
			});
			return;
		}
		const member = namespace[method];
		if (typeof member !== 'function') {
			write(socket, { id, error: phaseError(PHASES.NOT_CALLABLE, `'${method}' is not callable by the suite`) });
			return;
		}

		let result;
		try {
			result = await withTimeout(member(...args), callTimeoutMs);
		}
		catch (error) {
			if (error?.timeout) {
				write(socket, {
					id,
					error: phaseError(PHASES.INVOCATION_TIMEOUT,
						`'${method}' exceeded the ${callTimeoutMs}ms per-call shim limit`)
				});
			}
			else {
				write(socket, { id, error: phaseError(PHASES.INVOCATION_ERROR, describeThrown(error)) });
			}
			return;
		}

		let wireResult;
		try {
			wireResult = serialize(result, 'shim result');
		}
		catch (error) {
			write(socket, { id, error: phaseError(PHASES.NON_SERIALIZABLE_RESULT, error.message) });
			return;
		}
		write(socket, { id, result: wireResult });
	})();
}

function write(socket, message) {
	try {
		socket.write(JSON.stringify(message) + '\n');
	}
	catch {
		// The suite side went away mid-response; the connection teardown that
		// follows is the runner's job, not the server's.
		socket.destroy();
	}
}

function phaseError(phase, message) {
	return { phase, message };
}

/**
 * Turns whatever a solution threw into the message that reaches the suite.
 * The stack is deliberately not shipped: it names paths and line numbers the
 * student must not learn from a hidden call.
 * @param {unknown} thrown anything a student function can throw
 * @returns {string} a stable, short description
 */
function describeThrown(thrown) {
	if (thrown instanceof Error) {
		return thrown.message || String(thrown);
	}
	if (typeof thrown === 'string') {
		return thrown;
	}
	if (thrown !== null && typeof thrown === 'object') {
		try {
			return JSON.stringify(thrown);
		}
		catch {
			return String(thrown);
		}
	}
	return String(thrown);
}

/**
 * Rejects a promise that has not settled in time.
 * @param {Promise<unknown>} promise the work to bound
 * @param {number} ms ceiling in milliseconds
 * @returns {Promise<unknown>} the work, or a rejection once the ceiling passes
 */
function withTimeout(promise, ms) {
	let timer;
	const guard = new Promise((_resolve, reject) => {
		timer = setTimeout(() => reject(Object.assign(new Error('shim call timed out'), { timeout: true })), ms);
	});
	return Promise.race([promise, guard]).finally(() => clearTimeout(timer));
}

const runAsScript = process.argv[1] !== undefined && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (runAsScript) {
	createShimServer({
		socketPath: process.env.SHIM_SOCKET ?? DEFAULT_SOCKET,
		solutionPath: resolveSolutionPath(process.env.SOLUTION_PATH, DEFAULT_SOLUTION_PATH),
		callTimeoutMs: Number(process.env.SHIM_CALL_TIMEOUT_MS ?? DEFAULT_CALL_TIMEOUT_MS),
		log: (line) => process.stderr.write(line + '\n')
	}).catch((error) => {
		process.stderr.write(`The grading shim server could not start: ${error.stack ?? error}\n`);
		process.exitCode = 1;
	});
}