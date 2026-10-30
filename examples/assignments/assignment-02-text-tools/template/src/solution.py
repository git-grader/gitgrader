# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""Text tools.

Implement the small text transformations the README describes. The public
checks in template/public-tests cover the happy paths; the graded suite
covers more.
"""


def truncate(text, max_length):
    """Return the first max_length characters of text.

    Characters means user-perceived characters: an emoji or an accented
    letter is one character, however many storage units it occupies.
    """
    raise NotImplementedError("truncate")


def slugify(text):
    """Return a URL label for text.

    Lowercase, letters with marks reduced to their plain form, and every
    run of characters that are not letters or digits collapsed to one
    dash. Leading and trailing separators disappear.
    """
    raise NotImplementedError("slugify")


def title_case(text):
    """Return text with the first letter of every word capitalised.

    A word boundary is whitespace or a punctuation mark that joins words,
    such as the dash in quick-brown.
    """
    raise NotImplementedError("title_case")


def word_count(text):
    """Return how many words text holds.

    Any run of whitespace separates words; an input without words counts
    as zero.
    """
    raise NotImplementedError("word_count")


def reverse_words(text):
    """Return the words of text in reverse order, single-spaced."""
    raise NotImplementedError("reverse_words")


def is_palindrome(text):
    """Return True when text reads the same in both directions.

    Case and punctuation do not matter: compare only the letters and
    digits.
    """
    raise NotImplementedError("is_palindrome")
