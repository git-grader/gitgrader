# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""Reference solution for the text-tools assignment."""

import unicodedata


def truncate(text, max_length):
    return text[:max_length]


def slugify(text):
    folded = unicodedata.normalize("NFKD", text)
    plain = "".join(ch for ch in folded if not unicodedata.combining(ch))
    pieces = []
    current = []
    for ch in plain.lower():
        if ch.isalnum():
            current.append(ch)
        elif current:
            pieces.append("".join(current))
            current = []
    if current:
        pieces.append("".join(current))
    return "-".join(pieces)


def title_case(text):
    words = []
    for word in text.split(" "):
        pieces = []
        for part in word.split("-"):
            if part:
                pieces.append(part[:1].upper() + part[1:])
            else:
                pieces.append(part)
        words.append("-".join(pieces))
    return " ".join(words)


def word_count(text):
    return len(text.split())


def reverse_words(text):
    return " ".join(reversed(text.split()))


def is_palindrome(text):
    meaningful = [ch.lower() for ch in text if ch.isalnum()]
    return meaningful == meaningful[::-1]
