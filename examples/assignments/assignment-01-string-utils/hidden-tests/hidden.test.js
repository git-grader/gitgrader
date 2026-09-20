import { createShimClient } from "/opt/gitgrader-shim/client.js";

// This suite runs in the suite container of a shimmed round. Grading is split
// across two containers: the suite in this container, the submission in a
// sandbox container that never sees these hidden sources. The shim client in
// /opt/gitgrader-shim opens the socket the two containers share, and the proxy
// below reaches the submission's exports over it.
const { proxy } = await createShimClient();

it("h01 truncate preserves text at the maximum length", async () => {
  expect(await proxy.truncate("exact", 5)).toBe("exact");
});

it("h02 truncate counts Unicode characters rather than UTF-16 units", async () => {
  expect(await proxy.truncate("😀ab", 2)).toBe("😀a");
});

it("h03 slugify normalises accented letters", async () => {
  expect(await proxy.slugify("Crème Brûlée")).toBe("creme-brulee");
});

it("h04 slugify collapses punctuation and whitespace", async () => {
  expect(await proxy.slugify("Hello,   world!!!")).toBe("hello-world");
});

it("h05 titleCase capitalises hyphenated words", async () => {
  expect(await proxy.titleCase("the quick-brown FOX")).toBe(
    "The Quick-Brown Fox",
  );
});

it("h06 wordCount returns zero for empty text", async () => {
  expect(await proxy.wordCount("")).toBe(0);
});

it("h07 wordCount accepts mixed whitespace", async () => {
  expect(await proxy.wordCount("one\t two\nthree")).toBe(3);
});

it("h08 reverseWords normalises repeated whitespace", async () => {
  expect(await proxy.reverseWords("  one   two\tthree  ")).toBe(
    "three two one",
  );
});

it("h09 isPalindrome ignores case and punctuation", async () => {
  expect(await proxy.isPalindrome("A man, a plan, a canal: Panama!")).toBe(
    true,
  );
});

it("h10 isPalindrome rejects a non-palindrome", async () => {
  expect(await proxy.isPalindrome("OpenAI")).toBe(false);
});
