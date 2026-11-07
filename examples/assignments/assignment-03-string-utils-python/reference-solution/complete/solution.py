import re
import unicodedata


def truncate(text, max_length):
    if text is None:
        return None
    return text if len(text) <= max_length else text[:max_length]


def slugify(text):
    if text is None:
        return None
    normalised = unicodedata.normalize("NFD", text)
    without_marks = "".join(char for char in normalised if not unicodedata.combining(char))
    return re.sub(r"[^a-z0-9]+", "-", without_marks.lower()).strip("-")


def titleCase(text):
    if text is None:
        return None
    words = []
    for word in text.split():
        parts = word.split("-")
        titled = "-".join(part[:1].upper() + part[1:].lower() for part in parts if part)
        words.append(titled)
    return " ".join(words)


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
    cleaned = re.sub(r"[^A-Za-z0-9]", "", text).lower()
    return cleaned == cleaned[::-1]
