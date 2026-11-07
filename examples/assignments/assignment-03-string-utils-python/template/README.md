# String utilities (Python)

Implement the six functions in `src/solution.py`:

- `truncate(text, maxLength)` returns at most `maxLength` characters.
- `slugify(text)` returns a lowercase, hyphen-separated URL label.
- `titleCase(text)` capitalises each word, including words after hyphens.
- `wordCount(text)` counts whitespace-separated words.
- `reverseWords(text)` returns the words in reverse order, separated by one space.
- `isPalindrome(text)` compares letters and numbers without regard to case or punctuation.

The grading sandbox imports `src/solution.py` and serves every module-level
function, so keep the function names exactly as listed above.

Run the visible smoke test with any Python 3:

```sh
python3 public-tests/public_smoke.py
```

The score is `passed / total * 100`. Additional tests, not included in this
project, decide the final grade. Keep your implementation in `src/` and push
your work over Git when it is ready.
