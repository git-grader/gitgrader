import assert from 'node:assert/strict';
import test from 'node:test';

const solutionPath = process.env.SOLUTION_PATH ?? '/workspace/src/string-utils.js';
const solution = await import(solutionPath);

test('h01 truncate preserves text at the maximum length', () => {
  assert.equal(solution.truncate('exact', 5), 'exact');
});

test('h02 truncate counts Unicode characters rather than UTF-16 units', () => {
  assert.equal(solution.truncate('😀ab', 2), '😀a');
});

test('h03 slugify normalises accented letters', () => {
  assert.equal(solution.slugify('Crème Brûlée'), 'creme-brulee');
});

test('h04 slugify collapses punctuation and whitespace', () => {
  assert.equal(solution.slugify('Hello,   world!!!'), 'hello-world');
});

test('h05 titleCase capitalises hyphenated words', () => {
  assert.equal(solution.titleCase('the quick-brown FOX'), 'The Quick-Brown Fox');
});

test('h06 wordCount returns zero for empty text', () => {
  assert.equal(solution.wordCount(''), 0);
});

test('h07 wordCount accepts mixed whitespace', () => {
  assert.equal(solution.wordCount('one\t two\nthree'), 3);
});

test('h08 reverseWords normalises repeated whitespace', () => {
  assert.equal(solution.reverseWords('  one   two\tthree  '), 'three two one');
});

test('h09 isPalindrome ignores case and punctuation', () => {
  assert.equal(solution.isPalindrome('A man, a plan, a canal: Panama!'), true);
});

test('h10 isPalindrome rejects a non-palindrome', () => {
  assert.equal(solution.isPalindrome('OpenAI'), false);
});
