/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

const PLAIN_OBJECT = Object.prototype;

/**
 * Checks that a value may cross the shim boundary without being silently
 * mangled.
 *
 * Only JSON values travel between the sandbox and the suite: null, booleans,
 * finite numbers, strings, arrays of JSON values, and plain objects whose
 * values are JSON values. Everything else is rejected because serialising it
 * would either lose information (NaN, Infinity, undefined, functions, class
 * instances, Map/Set/Date, symbol-keyed members) or blow up entirely (BigInt,
 * circular structures). The reason the walk exists instead of a bare
 * {@code JSON.stringify} is that JSON.stringify silently turns NaN, Infinity
 * and undefined into JSON-safe artefacts instead of failing.
 *
 * @param {unknown} value the value to check
 * @param {string} what a label for the value, for the error message
 * @throws {TypeError} when the value is not a JSON value
 */
export function assertJsonSerializable(value, what) {
	const seen = new Set();
	const check = (node, path) => {
		if (node === null) {
			return;
		}
		const type = typeof node;
		if (type === 'string' || type === 'boolean') {
			return;
		}
		if (type === 'number') {
			if (!Number.isFinite(node)) {
				throw new TypeError(`${what} at ${path} is ${node}, which JSON cannot represent faithfully`);
			}
			return;
		}
		if (type === 'undefined') {
			throw new TypeError(`${what} at ${path} is undefined, which JSON has no value for`);
		}
		if (type !== 'object') {
			throw new TypeError(`${what} at ${path} has type '${type}', which cannot cross the shim`);
		}
		if (seen.has(node)) {
			throw new TypeError(`${what} at ${path} is circular and cannot cross the shim`);
		}
		seen.add(node);
		if (Array.isArray(node)) {
			node.forEach((child, index) => check(child, `${path}[${index}]`));
		}
		else {
			const prototype = Object.getPrototypeOf(node);
			if (prototype !== PLAIN_OBJECT && prototype !== null) {
				throw new TypeError(
					`${what} at ${path} is not a plain object and cannot cross the shim as a value`);
			}
			for (const key of Object.keys(node)) {
				check(node[key], `${path}.${key}`);
			}
		}
		seen.delete(node);
	};
	check(value, what);
}