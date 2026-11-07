public class PublicSmoke {

	public static void main(String[] args) {
		int failures = 0;
		failures += check("truncate", Solution.truncate("hello", 3), "hel");
		failures += check("wordCount", Solution.wordCount("a b c"), 3);
		failures += check("isPalindrome", Solution.isPalindrome("Racecar"), true);
		System.out.println(failures == 0 ? "public smoke: PASS" : "public smoke: " + failures + " FAILED");
		System.exit(failures == 0 ? 0 : 1);
	}

	private static int check(String name, Object actual, Object expected) {
		boolean ok = expected.equals(actual);
		System.out.println((ok ? "ok - " : "not ok - ") + name);
		return ok ? 0 : 1;
	}

}
