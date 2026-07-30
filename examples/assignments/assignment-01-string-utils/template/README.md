# String utilities

Implement the six exports in `src/string-utils.js`:

- `truncate(text, maxLength)` returns at most `maxLength` characters.
- `slugify(text)` returns a lowercase, hyphen-separated URL label.
- `titleCase(text)` capitalises each word, including words after hyphens.
- `wordCount(text)` counts whitespace-separated words.
- `reverseWords(text)` returns the words in reverse order, separated by one space.
- `isPalindrome(text)` compares letters and numbers without regard to case or punctuation.

Run the visible smoke tests with:

```sh
npm test
```

The score is `passed / total * 100`. Additional tests, not included in this
project, decide the final grade. Keep your implementation in `src/` and push
your work over Git when it is ready.
