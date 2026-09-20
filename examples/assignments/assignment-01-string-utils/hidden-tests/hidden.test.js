import assert from 'node:assert/strict';
import test from 'node:test';
import { createShimClient } from '/opt/gitgrader-shim/client.js';

// This suite runs in the suite container of a shimmed round. Grading is split
// across two containers: the suite in this container, the submission in a
// sandbox container that never sees these hidden sources. The shim client in
// /opt/gitgrader-shim opens the socket the two containers share, and the proxy
// below reaches the submission's exports over it.
const { proxy } = await createShimClient();

test('h01 truncate preserves text at the maximum length', async () => {
  assert.equal(await proxy.truncate('exact', 5), 'exact');
});

test('h02 truncate counts Unicode characters rather than UTF-16 units', async () => {
  assert.equal(await proxy.truncate('😀ab', 2), '😀a');
});

test('h03 slugify normalises accented letters', async () => {
  assert.equal(await proxy.slugify('Crème Brûlée'), 'creme-brulee');
});

test('h04 slugify collapses punctuation and whitespace', async () => {
  assert.equal(await proxy.slugify('Hello,   world!!!'), 'hello-world');
});

test('h05 titleCase capitalises hyphenated words', async () => {
  assert.equal(await proxy.titleCase('the quick-brown FOX'), 'The Quick-Brown Fox');
});

test('h06 wordCount returns zero for empty text', async () => {
  assert.equal(await proxy.wordCount(''), 0);
});

test('h07 wordCount accepts mixed whitespace', async () => {
  assert.equal(await proxy.wordCount('one\t two\nthree'), 3);
});

test('h08 reverseWords normalises repeated whitespace', async () => {
  assert.equal(await proxy.reverseWords('  one   two\tthree  '), 'three two one');
});

test('h09 isPalindrome ignores case and punctuation', async () => {
  assert.equal(await proxy.isPalindrome('A man, a plan, a canal: Panama!'), true);
});

test('h10 isPalindrome rejects a non-palindrome', async () => {
  assert.equal(await proxy.isPalindrome('OpenAI'), false);
});