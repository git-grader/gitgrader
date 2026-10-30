# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""Partial reference solution for the text-tools assignment.

Deliberately misses three hidden checks (h02, h07, h10) so the worked
example can show a 70% score.
"""

import unicodedata


def truncate(text, max_length):
    # Misses h02: slices UTF-16 code units via a len() that Python already
    # counts in characters -- kept simple so h02 fails on the emoji case.
    return text.encode("utf-16-le")[: max_length * 2].decode("utf-16-le", errors="ignore")


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
    # Misses h07: only ordinary spaces separate words here.
    return len([word for word in text.split(" ") if word])


def reverse_words(text):
    return " ".join(reversed(text.split()))


def is_palindrome(text):
    # Misses h10: every input is declared a palindrome.
    return True
