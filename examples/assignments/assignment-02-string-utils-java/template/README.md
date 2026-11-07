# String utilities (Java)

Implement the six `public static` methods in `src/Solution.java`:

- `truncate(text, maxLength)` returns at most `maxLength` characters.
- `slugify(text)` returns a lowercase, hyphen-separated URL label.
- `titleCase(text)` capitalises each word, including words after hyphens.
- `wordCount(text)` counts whitespace-separated words.
- `reverseWords(text)` returns the words in reverse order, separated by one space.
- `isPalindrome(text)` compares letters and numbers without regard to case or punctuation.

The grading sandbox compiles `src/Solution.java` with the JDK and serves every
`public static` method, so keep the class named `Solution` in the default
package and keep the signatures above.

Run the visible smoke test with any JDK 17+:

```sh
javac -d out src/Solution.java public-tests/PublicSmoke.java && java -cp out PublicSmoke
```

The score is `passed / total * 100`. Additional tests, not included in this
project, decide the final grade. Keep your implementation in `src/` and push
your work over Git when it is ready.
