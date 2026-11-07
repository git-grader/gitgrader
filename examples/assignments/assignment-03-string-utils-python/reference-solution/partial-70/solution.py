"""Intentional 70% implementation: passes seven checks and fails three -
h03 (slugify does not normalise accents), h05 (titleCase does not capitalise
after a hyphen), and h09 (palindrome check does not strip punctuation).
See the hidden manifest for the exact test names."""

import re


def truncate(text, max_length):
    if text is None:
        return None
    return text if len(text) <= max_length else text[:max_length]


def slugify(text):
    if text is None:
        return None
    # No accent normalisation: accented letters become separators, so h03 fails.
    return re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")


def titleCase(text):
    if text is None:
        return None
    # Capitalises only the first letter of each whitespace-separated word,
    # so "quick-brown" stays "Quick-brown" and h05 fails.
    return " ".join(word[:1].upper() + word[1:].lower() for word in text.split())


def wordCount(text):
    if text is None:
        return 0
    return len(text.split())


def reverseWords(text):
    if text is None:
        return None
    return " ".join(reversed(text.split()))


def isPalindrome(text):
    if text is None:
        return False
    # No punctuation/case stripping: h09 fails.
    lower = text.lower()
    return lower == lower[::-1]
