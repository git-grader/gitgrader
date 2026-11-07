"""Visible smoke test for the Python string-utils template.

Run from the template root:  python3 public-tests/public_smoke.py
It imports src/solution.py directly (no shim) and prints TAP-style lines.
"""

import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "src"))

import solution  # noqa: E402


def check(name, actual, expected):
    ok = actual == expected
    print(("ok - " if ok else "not ok - ") + name)
    return 0 if ok else 1


def main():
    failures = 0
    failures += check("truncate", solution.truncate("hello", 3), "hel")
    failures += check("wordCount", solution.wordCount("a b c"), 3)
    failures += check("isPalindrome", solution.isPalindrome("Racecar"), True)
    print("public smoke: " + ("PASS" if failures == 0 else f"{failures} FAILED"))
    sys.exit(0 if failures == 0 else 1)


if __name__ == "__main__":
    main()
