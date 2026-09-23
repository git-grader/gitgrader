/*
 * Copyright the GitGrader contributors.
 * SPDX-License-Identifier: Apache-2.0
 */

const PLAIN_OBJECT = Object.prototype;

// The only place a plain object may meet the marker key is when the codec put
// it there itself; the key is chosen so this cannot be confused with teaching
// data. A wire value that is an object with exactly this one own key encodes an
// otherwise-unrepresentable value, so it must be decoded back, never delivered
// as-is.
const MARKER = '$gitgrader';
const KIND_BIGINT = 'bigint';
const KIND_UNDEFINED = 'undefined';
const KIND_PROTO_OBJECT = 'proto-object';

/**
 * Encodes a value into its wire representation.
 *
 * The wire is still plain JSON on the wire, but a small set of values that
 * JSON cannot carry are represented by tagged markers, so the codec carries
 * them faithfully in both directions instead of refusing them:
 *
 * - `bigint` becomes `{ [MARKER]: ['bigint', '<decimal digits>'] }`
 * - `undefined` becomes `{ [MARKER]: ['undefined'] }`
 * - an object whose prototype is neither `Object.prototype` nor `null` becomes
 *   `{ [MARKER]: ['proto-object', <encoded prototype>, <encoded own fields>] }`
 *   and is rebuilt with {@code Object.create} on the suite side
 *
 * Everything else keeps its JSON shape: `null`, booleans, finite numbers,
 * strings, arrays of values, and plain objects whose values are values. Values
 * that a faithful representation cannot carry at all are still rejected with a
 * {@link TypeError}: `NaN`, `Infinity`, functions, symbols, class instances
 * (`Date`, `Map`, `Set`, ...), symbol-keyed members and circular structures.
 * Rejecting by structural walk instead of by stringify is deliberate:
 * {@code JSON.stringify} silently turns `NaN`, `Infinity` and `undefined` into
 * JSON-safe artefacts.
 *
 * @param {unknown} value the value to encode
 * @param {string} what a label for the value, for error messages
 * @returns {unknown} the wire representation, a JSON value
 * @throws {TypeError} when the value cannot cross the shim faithfully
 */
export function serialize(value, what) {
	const encode = (node, path, seen) => {
		if (node === null) {
			return null;
		}
		const type = typeof node;
		if (type === 'string' || type === 'boolean') {
			return node;
		}
		if (type === 'number') {
			if (!Number.isFinite(node)) {
				throw new TypeError(`${what} at ${path} is ${node}, which the shim cannot carry faithfully`);
			}
			return node;
		}
		if (type === 'bigint') {
			return { [MARKER]: [KIND_BIGINT, node.toString(10)] };
		}
		if (type === 'undefined') {
			return { [MARKER]: [KIND_UNDEFINED] };
		}
		if (type !== 'object') {
			throw new TypeError(`${what} at ${path} has type '${type}', which cannot cross the shim`);
		}
		if (seen.has(node)) {
			throw new TypeError(`${what} at ${path} is circular and cannot cross the shim`);
		}
		seen.add(node);
		let encoded;
		if (Array.isArray(node)) {
			encoded = node.map((child, index) => encode(child, `${path}[${index}]`, seen));
		}
		else {
			// Only plain data objects and objects built by Object.create() from a
			// data prototype travel as values. Built-ins that masquerade as
			// objects (Date, Map, Set, RegExp, Promise, typed arrays, ...) are
			// class instances whose state a prototype markup could not rebuild,
			// so reject them instead of silently dropping their inner state.
			if (Object.prototype.toString.call(node) !== '[object Object]') {
				throw new TypeError(
					`${what} at ${path} is a ${Object.prototype.toString.call(node).slice(8, -1)} ` +
						'instance and cannot cross the shim as a value');
			}
			const prototype = Object.getPrototypeOf(node);
			if (prototype !== PLAIN_OBJECT && prototype !== null) {
				encoded = {
					[MARKER]: [
						KIND_PROTO_OBJECT,
						encode(prototype, `${path} [[prototype]]`, seen),
						ownFields(node, path, seen, encode, what)
					]
				};
			}
			else {
				encoded = ownFields(node, path, seen, encode, what);
			}
		}
		seen.delete(node);
		return encoded;
	};
	return encode(value, what, new Set());
}

/**
 * Encodes the own enumerable string-keyed fields of a plain-object node.
 * Skeleton members are refused because skipping them would silently drop data.
 * @param {object} node the object to read
 * @param {string} path diagnostic path prefix
 * @param {Set<object>} seen objects currently being encoded, for cycle checks
 * @param {(node: unknown, path: string, seen: Set<object>) => unknown} encode
 * @param {string} what a label for the value, for error messages
 * @returns {object} the encoded object
 */
function ownFields(node, path, seen, encode, what) {
	if (Object.getOwnPropertySymbols(node).length > 0) {
		throw new TypeError(`${what} at ${path} carries symbol-keyed members and cannot cross the shim`);
	}
	const encoded = {};
	for (const key of Object.keys(node)) {
		encoded[key] = encode(node[key], `${path}.${key}`, seen);
	}
	return encoded;
}

/**
 * Decodes a parsed wire representation back into JavaScript values, inverting
 * {@link serialize}: marker objects become `bigint`, `undefined` and
 * custom-prototype objects; everything else is returned as-is.
 *
 * @param {unknown} node a value parsed from the wire
 * @returns {unknown} the reconstructed JavaScript value
 * @throws {TypeError} when the wire carries a malformed marker
 */
export function deserialize(node) {
	if (node === null) {
		return null;
	}
	const type = typeof node;
	if (type === 'string' || type === 'boolean' || type === 'number') {
		return node;
	}
	if (type !== 'object') {
		throw new TypeError(`The shim cannot decode a value of type '${type}'`);
	}
	if (Array.isArray(node)) {
		return node.map(deserialize);
	}
	const keys = Object.keys(node);
	if (keys.length === 1 && keys[0] === MARKER) {
		return decodeMarker(node[MARKER]);
	}
	const decoded = {};
	for (const key of keys) {
		decoded[key] = deserialize(node[key]);
	}
	return decoded;
}

/**
 * Rebuilds a tagged value from its marker while checking the marker shape, so
 * an object that merely carries the marker key without the envelope fails
 * loudly instead of arriving mangled.
 * @param {unknown} marker the marker payload
 * @returns {unknown} the reconstructed value
 */
function decodeMarker(marker) {
	if (Array.isArray(marker) && typeof marker[0] === 'string') {
		const [kind, ...rest] = marker;
		if (kind === KIND_BIGINT && rest.length === 1 && typeof rest[0] === 'string') {
			return BigInt(rest[0]);
		}
		if (kind === KIND_UNDEFINED && rest.length === 0) {
			return undefined;
		}
		if (kind === KIND_PROTO_OBJECT && rest.length === 2) {
			return Object.assign(Object.create(deserialize(rest[0])), deserialize(rest[1]));
		}
	}
	throw new TypeError('The shim wire carries a malformed value marker');
}