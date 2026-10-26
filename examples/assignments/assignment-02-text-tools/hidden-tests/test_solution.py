# Copyright the GitGrader contributors.
# SPDX-License-Identifier: Apache-2.0

"""Hidden checks for the text-tools assignment.

This suite runs in the suite container of a shimmed round. Grading is split
across two containers: the suite in this container, the submission in a
sandbox container that never sees these hidden sources. The shim client in
/opt/gitgrader-shim opens the socket the two containers share, and the proxy
below reaches the submission's module over it.

Every test name is declared in manifest.json; the backend refuses to score a
name the manifest does not declare.
"""

from client import create_shim_client

proxy, close = create_shim_client()


def test_h01_truncate_preserves_text_at_the_maximum_length():
    assert proxy.truncate("exact", 5) == "exact"


def test_h02_truncate_counts_unicode_characters_rather_than_utf_16_units():
    assert proxy.truncate("😀ab", 2) == "😀a"


def test_h03_slugify_normalises_accented_letters():
    assert proxy.slugify("Crème Brûlée") == "creme-brulee"


def test_h04_slugify_collapses_punctuation_and_whitespace():
    assert proxy.slugify("Hello,   world!!!") == "hello-world"


def test_h05_title_case_capitalises_hyphenated_words():
    assert proxy.title_case("the quick-brown FOX") == "The Quick-Brown Fox"


def test_h06_word_count_returns_zero_for_empty_text():
    assert proxy.word_count("") == 0


def test_h07_word_count_accepts_mixed_whitespace():
    assert proxy.word_count("one\t two\nthree") == 3


def test_h08_reverse_words_normalises_repeated_whitespace():
    assert proxy.reverse_words("  one   two\tthree  ") == "three two one"


def test_h09_is_palindrome_ignores_case_and_punctuation():
    assert proxy.is_palindrome("A man, a plan, a canal: Panama!") is True


def test_h10_is_palindrome_rejects_a_non_palindrome():
    assert proxy.is_palindrome("OpenAI") is False
