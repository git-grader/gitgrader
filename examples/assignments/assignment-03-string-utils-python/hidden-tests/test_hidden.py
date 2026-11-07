import sys

sys.path.insert(0, "/opt/gitgrader-shim")

from client import create_shim_client  # noqa: E402

# This suite runs in the suite container of a shimmed round. Grading is split
# across two containers: the suite in this container, the submission in a
# sandbox container that never sees these hidden sources. The shim client in
# /opt/gitgrader-shim opens the socket the two containers share, and each call
# below reaches one of the submission's module-level functions over it.
shim = create_shim_client()


def test_h01():
    assert shim.truncate("exact", 5) == "exact"


def test_h02():
    assert shim.truncate("😀ab", 2) == "😀a"


def test_h03():
    assert shim.slugify("Crème Brûlée") == "creme-brulee"


def test_h04():
    assert shim.slugify("Hello,   world!!!") == "hello-world"


def test_h05():
    assert shim.titleCase("the quick-brown FOX") == "The Quick-Brown Fox"


def test_h06():
    assert shim.wordCount("") == 0


def test_h07():
    assert shim.wordCount("one\t two\nthree") == 3


def test_h08():
    assert shim.reverseWords("  one   two\tthree  ") == "three two one"


def test_h09():
    assert shim.isPalindrome("A man, a plan, a canal: Panama!") is True


def test_h10():
    assert shim.isPalindrome("OpenAI") is False
