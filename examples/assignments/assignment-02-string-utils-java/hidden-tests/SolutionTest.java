import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// This suite runs in the suite container of a shimmed round. Grading is split
// across two containers: the suite in this container, the submission in a
// sandbox container that never sees these hidden sources. ShimClient in
// /opt/gitgrader-shim opens the socket the two containers share, and each call
// below reaches one of the submission's public static methods over it.
class SolutionTest {

	private static ShimClient shim;

	@BeforeAll
	static void openShim() {
		shim = ShimClient.connect();
	}

	@Test
	@DisplayName("h01 truncate preserves text at the maximum length")
	void h01() {
		assertEquals("exact", shim.call("truncate", "exact", 5));
	}

	@Test
	@DisplayName("h02 truncate counts Unicode characters rather than UTF-16 units")
	void h02() {
		assertEquals("😀a", shim.call("truncate", "😀ab", 2));
	}

	@Test
	@DisplayName("h03 slugify normalises accented letters")
	void h03() {
		assertEquals("creme-brulee", shim.call("slugify", "Crème Brûlée"));
	}

	@Test
	@DisplayName("h04 slugify collapses punctuation and whitespace")
	void h04() {
		assertEquals("hello-world", shim.call("slugify", "Hello,   world!!!"));
	}

	@Test
	@DisplayName("h05 titleCase capitalises hyphenated words")
	void h05() {
		assertEquals("The Quick-Brown Fox", shim.call("titleCase", "the quick-brown FOX"));
	}

	@Test
	@DisplayName("h06 wordCount returns zero for empty text")
	void h06() {
		assertEquals(0, shim.call("wordCount", ""));
	}

	@Test
	@DisplayName("h07 wordCount accepts mixed whitespace")
	void h07() {
		assertEquals(3, shim.call("wordCount", "one\t two\nthree"));
	}

	@Test
	@DisplayName("h08 reverseWords normalises repeated whitespace")
	void h08() {
		assertEquals("three two one", shim.call("reverseWords", "  one   two\tthree  "));
	}

	@Test
	@DisplayName("h09 isPalindrome ignores case and punctuation")
	void h09() {
		assertTrue((Boolean) shim.call("isPalindrome", "A man, a plan, a canal: Panama!"));
	}

	@Test
	@DisplayName("h10 isPalindrome rejects a non-palindrome")
	void h10() {
		assertFalse((Boolean) shim.call("isPalindrome", "OpenAI"));
	}

}
