/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

import assert from 'node:assert/strict';
import { mkdtemp, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { test } from 'node:test';

import { createShimClient, ShimError } from './client.js';
import { PHASES } from './phases.js';
import { createShimServer } from './server.js';
import { assertJsonSerializable } from './serializable.js';

const CONNECT_TIMEOUT_MS = 2_000;

/**
 * Runs a shim server in front of a hand-written solution module and hands the
 * test the connected client, cleaning both up afterwards.
 * @param {import('node:test').TestContext} context the running test
 * @param {string} solutionSource the module source to serve
 * @returns {Promise<{ client: Awaited<ReturnType<typeof createShimClient>>, socketPath: string }>}
 */
async function pair(context, solutionSource) {
	const directory = await mkdtemp(join(tmpdir(), 'gitgrader-shim-'));
	context.after(async () => rm(directory, { recursive: true, force: true }));

	const solutionPath = join(directory, 'solution.js');
	await writeFile(solutionPath, solutionSource);
	const socketPath = join(directory, 'runner.sock');

	const server = await createShimServer({ socketPath, solutionPath });
	context.after(async () => server.close());

	const client = await createShimClient({ socketPath, connectTimeoutMs: CONNECT_TIMEOUT_MS });
	context.after(() => client.close());

	return { client, server, socketPath };
}

const SOLUTION = String.raw`
  export function add(a, b) {
    return a + b;
  }

  export async function slow(value) {
    await new Promise((resolve) => setTimeout(resolve, 5));
    return value;
  }

  export function struct() {
    return { letters: ['a', 'b'], nested: { plus: 1 } };
  }

  export function boom() {
    throw new Error('boom from the submission');
  }

  export function big() {
    return 1n;
  }

  export function closure() {
    return () => 'secret';
  }

  export function nan() {
    return NaN;
  }

  export function undef() {
    return undefined;
  }

  export function circular() {
    const object = { label: 'x' };
    object.self = object;
    return object;
  }

  export function instance() {
    return { timestamp: new Date(0) };
  }

  export const ANSWER = 42;
`;

test('calls the solution exports across the socket and returns JSON values', async (context) => {
	const { client } = await pair(context, SOLUTION);

	assert.equal(await client.proxy.add(1, 2), 3);
	assert.equal(await client.proxy.slow('x'), 'x');
	assert.deepEqual(await client.proxy.struct(), { letters: ['a', 'b'], nested: { plus: 1 } });
});

test('carries a thrown error as an invocation-error without the stack', async (context) => {
	const { client } = await pair(context, SOLUTION);

	await assert.rejects(client.proxy.boom(), (error) => {
		assert.ok(error instanceof ShimError);
		assert.equal(error.phase, PHASES.INVOCATION_ERROR);
		assert.equal(error.message, 'boom from the submission');
		assert.equal(error.message.includes('solution.js'), false);
		return true;
	});
});

test('refuses a call to a member the solution does not export', async (context) => {
	const { client } = await pair(context, SOLUTION);

	await assert.rejects(client.proxy.thereIsNoSuchExport(), (error) => {
		assert.equal(error.phase, PHASES.NO_SUCH_MEMBER);
		return true;
	});
});

test('refuses to call an export that is not a function', async (context) => {
	const { client } = await pair(context, SOLUTION);

	await assert.rejects(client.proxy.ANSWER(), (error) => {
		assert.equal(error.phase, PHASES.NOT_CALLABLE);
		return true;
	});
});

test('rejects non-serialisable arguments before sending, without tearing the stream', async (context) => {
	const { client } = await pair(context, SOLUTION);

	await assert.rejects(client.proxy.add(Symbol('marker'), 1), (error) => {
		assert.equal(error.phase, PHASES.NON_SERIALIZABLE_ARGS);
		return true;
	});
	// The stream must still be usable, or a single bad call would fail the rest
	// of the suite's tests.
	assert.equal(await client.proxy.add(1, 2), 3);
});

test('rejects results that JSON would mangle or refuse', async (context) => {
	const { client } = await pair(context, SOLUTION);

	for (const member of ['big', 'closure', 'nan', 'undef', 'circular', 'instance']) {
		await assert.rejects(client.proxy[member](), (error) => {
			assert.equal(error.phase, PHASES.NON_SERIALIZABLE_RESULT, `expected ${member} to fail so`);
			return true;
		});
	}
});

test('correlates concurrent calls by id rather than by arrival order', async (context) => {
	const { client } = await pair(context, SOLUTION);

	const results = await Promise.all([1, 2, 3, 4, 5].map((value) => client.proxy.add(value, value)));
	assert.deepEqual(results, [2, 4, 6, 8, 10]);
});

test('fails calls with connection-closed once the client or the server is gone', async (context) => {
	const { client, server, socketPath } = await pair(context, SOLUTION);

	assert.equal(await client.proxy.add(1, 2), 3);
	// Closing the client is the deterministic half: a later call cannot write.
	client.close();
	await assert.rejects(client.proxy.add(3, 4), (error) => {
		assert.equal(error.phase, PHASES.CONNECTION_CLOSED);
		return true;
	});

	// Killing the server behind a live client must reject later calls too,
	// whether the write fails synchronously or the close event lands first.
	const second = await createShimClient({ socketPath, connectTimeoutMs: CONNECT_TIMEOUT_MS });
	context.after(() => second.close());
	await server.close();
	await assert.rejects(second.proxy.add(5, 6), (error) => {
		assert.equal(error.phase, PHASES.CONNECTION_CLOSED);
		return true;
	});
});

test('connect-timeout when the sandbox side never appears', async (context) => {
	const directory = await mkdtemp(join(tmpdir(), 'gitgrader-shim-'));
	context.after(() => rm(directory, { recursive: true, force: true }));
	const missing = join(directory, 'no-such.sock');

	await assert.rejects(createShimClient({ socketPath: missing, connectTimeoutMs: 200 }), (error) => {
		assert.ok(error instanceof ShimError);
		assert.equal(error.phase, PHASES.CONNECT_TIMEOUT);
		return true;
	});
});

test('the value contract admits plain JSON values and rejects the rest', () => {
	for (const [value, name] of [
		[null, 'null'],
		[false, 'boolean'],
		[17, 'integer'],
		[1.5, 'decimal'],
		['text', 'string'],
		[[1, 'two', null], 'array'],
		[{ nested: { list: [true] } }, 'plain object']
	]) {
		assert.doesNotThrow(() => assertJsonSerializable(value, name));
	}

	for (const [value, name] of [
		[NaN, 'NaN'],
		[Infinity, 'Infinity'],
		[undefined, 'undefined'],
		[1n, 'bigint'],
		[Symbol('x'), 'symbol'],
		[() => null, 'function'],
		[new Date(0), 'class instance'],
		[new Map([[1, 2]]), 'Map'],
		[new Set([1]), 'Set'],
		[[undefined], 'undefined inside an array'],
		[{ key: Infinity }, 'Infinity inside an object']
	]) {
		assert.throws(() => assertJsonSerializable(value, name), TypeError, name);
	}

	const circular = { label: 'x' };
	circular.self = circular;
	assert.throws(() => assertJsonSerializable(circular, 'circular object'), TypeError);
});