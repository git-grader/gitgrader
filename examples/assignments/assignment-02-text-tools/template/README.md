# Text tools

Implement six small text transformations in `src/solution.py`. Each function
has a docstring that pins down its behaviour; the public checks in
`public-tests/` cover the happy paths and are yours to run and read.

## Functions

| Function | One-line contract |
| --- | --- |
| `truncate(text, max_length)` | first `max_length` characters of `text` |
| `slugify(text)` | lowercase URL label, accents folded, separators collapsed |
| `title_case(text)` | first letter of every word capitalised |
| `word_count(text)` | number of whitespace-separated words |
| `reverse_words(text)` | words in reverse order, single-spaced |
| `is_palindrome(text)` | same forwards and backwards, ignoring case and punctuation |

A character means a user-perceived character: `"😀"` is one character even
though it occupies more than one UTF-16 unit and more than one byte.

## Running the public checks

```console
$ python -m pytest public-tests -q
```

No third-party packages are required; the graded environment bakes pytest
into the runtime image and runs without network access.
