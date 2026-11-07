// Intentional 70% implementation: passes seven checks and fails three -
// h02 (UTF-16-unit truncation splits the surrogate pair), h03 (slugify keeps
// accented letters as separators instead of normalising them), and h09
// (palindrome check does not strip punctuation). See the hidden manifest.
public class Solution {

	public static String truncate(String text, int maxLength) {
		if (text == null) {
			return null;
		}
		// Naive UTF-16-unit truncation: splits surrogate pairs, so h02 fails.
		return text.substring(0, Math.min(maxLength, text.length()));
	}

	public static String slugify(String text) {
		if (text == null) {
			return null;
		}
		// No accent normalisation: accented letters become separators, so h03 fails.
		return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
	}

	public static String titleCase(String text) {
		if (text == null) {
			return null;
		}
		String[] words = text.trim().split("\\s+");
		StringBuilder result = new StringBuilder();
		for (String word : words) {
			if (word.isEmpty()) {
				continue;
			}
			String[] parts = word.split("-");
			StringBuilder titled = new StringBuilder();
			for (int i = 0; i < parts.length; i++) {
				if (i > 0) {
					titled.append('-');
				}
				String part = parts[i];
				if (!part.isEmpty()) {
					titled.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1).toLowerCase());
				}
			}
			if (result.length() > 0) {
				result.append(' ');
			}
			result.append(titled);
		}
		return result.toString();
	}

	public static int wordCount(String text) {
		if (text == null) {
			return 0;
		}
		String trimmed = text.trim();
		return trimmed.isEmpty() ? 0 : trimmed.split("\\s+").length;
	}

	public static String reverseWords(String text) {
		if (text == null) {
			return null;
		}
		String[] words = text.trim().split("\\s+");
		java.util.List<String> list = new java.util.ArrayList<>();
		for (String word : words) {
			if (!word.isEmpty()) {
				list.add(word);
			}
		}
		java.util.Collections.reverse(list);
		return String.join(" ", list);
	}

	public static boolean isPalindrome(String text) {
		if (text == null) {
			return false;
		}
		// No punctuation/case stripping: "A man, a plan..." is not a palindrome, so h09 fails.
		String lower = text.toLowerCase();
		return new StringBuilder(lower).reverse().toString().equals(lower);
	}

}
