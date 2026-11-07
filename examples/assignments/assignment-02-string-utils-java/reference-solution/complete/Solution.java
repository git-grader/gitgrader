public class Solution {

	public static String truncate(String text, int maxLength) {
		if (text == null) {
			return null;
		}
		int[] codePoints = text.codePoints().toArray();
		if (codePoints.length <= maxLength) {
			return text;
		}
		return new String(codePoints, 0, maxLength);
	}

	public static String slugify(String text) {
		if (text == null) {
			return null;
		}
		String normalised = java.text.Normalizer.normalize(text, java.text.Normalizer.Form.NFD)
			.replaceAll("\\p{M}+", "");
		return normalised.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
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
		String cleaned = text.replaceAll("[^A-Za-z0-9]", "").toLowerCase();
		return new StringBuilder(cleaned).reverse().toString().equals(cleaned);
	}

}
