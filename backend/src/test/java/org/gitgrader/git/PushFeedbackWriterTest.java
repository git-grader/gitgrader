/*
 * Copyright the GitGrader contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gitgrader.git;

import java.util.List;

import org.gitgrader.git.PushFeedback.PushOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link PushFeedbackWriter}.
 *
 * <p>
 * This block is the primary user interface of the platform: every student sees it on
 * every submission, usually before they ever open the web UI. It is also the one place
 * where a leak of hidden test material would be handed directly to the student, so the
 * confidentiality assertions here matter as much as the formatting ones.
 */
class PushFeedbackWriterTest {

	private final PushFeedbackWriter writer = new PushFeedbackWriter();

	@Test
	@DisplayName("reports 7 of 10 tests as exactly 70.0 %")
	void reportsTheWorkedExample() {
		// The acceptance criterion from the brief, asserted on the exact text a student
		// sees rather than on an internal number.
		String output = this.writer.renderText(graded(7, 10, "70.0"));

		assertThat(output).contains("7 of 10 tests passed");
		assertThat(output).contains("Score: 70.0 %");
		assertThat(output).doesNotContain("70.00000");
	}

	@Test
	@DisplayName("shows the verified badge and the result link")
	void showsBadgeAndLink() {
		String output = this.writer.renderText(graded(10, 10, "100.0"));

		assertThat(output).contains("Signature: Verified");
		assertThat(output).contains("Detailed result:");
		assertThat(output).contains("https://grader.example.org/result/");
	}

	@Test
	@DisplayName("offers only the result link while grading is still running")
	void pendingRunOffersOnlyTheLink() {
		PushFeedback feedback = new PushFeedback("GitGrader", "Max Muster", "Assignment 01", "8f31c20", "Verified",
				PushOutcome.ACCEPTED_PENDING, null, null, null, "https://grader.example.org/result/abc", List.of(),
				null);

		String output = this.writer.renderText(feedback);

		assertThat(output).contains("Submission accepted.");
		assertThat(output).contains("The full check is running.");
		assertThat(output).contains("https://grader.example.org/result/abc");
		assertThat(output).doesNotContain("Score:");
	}

	@Test
	@DisplayName("states plainly that an infrastructure failure is not the student's fault")
	void infrastructureFailureDoesNotBlameTheStudent() {
		// An infrastructure failure must never read like a failed attempt. Students
		// reasonably panic when a submission screen looks like a rejection.
		PushFeedback feedback = new PushFeedback("GitGrader", "Max Muster", "Assignment 01", "8f31c20", "Verified",
				PushOutcome.INFRASTRUCTURE_ERROR, null, null, null, "https://grader.example.org/result/abc", List.of(),
				null);

		String output = this.writer.renderText(feedback);

		assertThat(output).contains("not with your work");
		assertThat(output).contains("No attempt has been counted against you.");
		assertThat(output).doesNotContain("tests passed");
	}

	@Test
	@DisplayName("explains a rejection instead of failing silently")
	void rejectionCarriesItsReason() {
		PushFeedback feedback = new PushFeedback("GitGrader", "Max Muster", "Assignment 01", "8f31c20", "Unverified",
				PushOutcome.REJECTED, null, null, null, null, List.of(),
				"Commit 8f31c20 is not signed. Configure SSH commit signing and push again.");

		String output = this.writer.renderText(feedback);

		assertThat(output).contains("Your push was rejected.");
		assertThat(output).contains("not signed");
		assertThat(output).doesNotContain("Detailed result:");
	}

	@Test
	@DisplayName("renders only manifest-authored hints, never raw failure text")
	void rendersOnlyCuratedHints() {
		PushFeedback feedback = graded(7, 10, "70.0", List.of("Check what happens when the text is empty."));

		String output = this.writer.renderText(feedback);

		assertThat(output).contains("Hints:");
		assertThat(output).contains("- Check what happens when the text is empty.");
		// Nothing that could identify a hidden test may appear: not its name, not a file
		// path, not an expected value.
		assertThat(output).doesNotContain("hidden");
		assertThat(output).doesNotContain("/opt/");
		assertThat(output).doesNotContain("expected");
	}

	@Test
	@DisplayName("emits no bare carriage return, because git prefixes every line")
	void producesCleanSidebandLines() {
		// git writes each line as "remote: <line>". A stray CR would corrupt the terminal
		// output of every push.
		List<String> lines = this.writer.render(graded(7, 10, "70.0"));

		assertThat(lines).isNotEmpty();
		assertThat(lines).allSatisfy((line) -> assertThat(line).doesNotContain("\r").doesNotContain("\n"));
	}

	@Test
	@DisplayName("leads with the configured product name, never a hard-coded one")
	void usesTheConfiguredProductName() {
		PushFeedback feedback = new PushFeedback("Coursework Checker", "Max Muster", "Assignment 01", "8f31c20",
				"Verified", PushOutcome.GRADED, 7, 10, "70.0", null, List.of(), null);

		assertThat(this.writer.render(feedback).getFirst()).isEqualTo("Coursework Checker");
	}

	private static PushFeedback graded(int passed, int total, String percent) {
		return graded(passed, total, percent, List.of());
	}

	private static PushFeedback graded(int passed, int total, String percent, List<String> hints) {
		return new PushFeedback("GitGrader", "Max Muster", "Assignment 01", "8f31c20", "Verified", PushOutcome.GRADED,
				passed, total, percent, "https://grader.example.org/result/abc123", hints, null);
	}

}
