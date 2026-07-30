import assert from 'node:assert/strict';
import test from 'node:test';
import {
  isPalindrome,
  reverseWords,
  slugify,
  titleCase,
  truncate,
  wordCount,
} from '../src/string-utils.js';

test('truncate returns text that fits within the maximum length', () => {
  assert.equal(truncate('hello', 5), 'hello');
});

test('slugify creates a lowercase hyphenated label', () => {
  assert.equal(slugify('Hello World'), 'hello-world');
});

test('titleCase capitalises words', () => {
  assert.equal(titleCase('hello world'), 'Hello World');
});

test('wordCount counts whitespace-separated words', () => {
  assert.equal(wordCount('one two three'), 3);
});

test('reverseWords reverses word order', () => {
  assert.equal(reverseWords('one two three'), 'three two one');
});

test('isPalindrome recognises a simple palindrome', () => {
  assert.equal(isPalindrome('level'), true);
});
