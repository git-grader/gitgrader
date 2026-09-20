import {
  isPalindrome,
  reverseWords,
  slugify,
  titleCase,
  truncate,
  wordCount,
} from "../src/string-utils.js";

it("truncate returns text that fits within the maximum length", () => {
  expect(truncate("hello", 5)).toBe("hello");
});

it("slugify creates a lowercase hyphenated label", () => {
  expect(slugify("Hello World")).toBe("hello-world");
});

it("titleCase capitalises words", () => {
  expect(titleCase("hello world")).toBe("Hello World");
});

it("wordCount counts whitespace-separated words", () => {
  expect(wordCount("one two three")).toBe(3);
});

it("reverseWords reverses word order", () => {
  expect(reverseWords("one two three")).toBe("three two one");
});

it("isPalindrome recognises a simple palindrome", () => {
  expect(isPalindrome("level")).toBe(true);
});
