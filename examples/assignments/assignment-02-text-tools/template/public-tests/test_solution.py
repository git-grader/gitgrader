# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""Public checks for the text-tools assignment.

These are the student-visible counterparts of the graded suite: they cover
the happy paths from the README so a submission can be sanity-checked
before pushing. The graded suite keeps its harder cases out of the student
project and out of this directory.
"""

from src.solution import (
    is_palindrome,
    reverse_words,
    slugify,
    title_case,
    truncate,
    word_count,
)


def test_truncate_keeps_the_requested_prefix():
    assert truncate("hello world", 5) == "hello"


def test_truncate_of_a_short_text_is_the_text():
    assert truncate("hi", 5) == "hi"


def test_slugify_lowercases_and_joins_with_dashes():
    assert slugify("Hello World") == "hello-world"


def test_title_case_capitalises_each_word():
    assert title_case("hello world") == "Hello World"


def test_word_count_splits_on_spaces():
    assert word_count("one two three") == 3


def test_reverse_words_reverses_the_order():
    assert reverse_words("one two three") == "three two one"
