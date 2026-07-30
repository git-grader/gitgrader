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

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

/**
 * Renders the block a student sees in the output of {@code git push}.
 *
 * <p>
 * This is the primary user interface of the whole platform. It is the one screen every
 * student sees on every submission, usually before they ever open the web UI, so it is
 * worth more care than its size suggests.
 *
 * <p>
 * Two constraints shape the implementation. First, git prefixes every line of a side-band
 * message with {@code remote: }, so the text has to read well with that prefix and must
 * never contain a bare carriage return. Second, nothing derived from the hidden test
 * suite may appear here beyond a count and a didactic hint - this output is delivered
 * over an unauthenticated-to-the-student channel and is trivially scriptable, which makes
 * it an attractive oracle for reconstructing the secret tests.
 */
@Component
public class PushFeedbackWriter {

	/** Width of the separator rule, chosen to fit an 80 column terminal. */
	private static final int RULE_WIDTH = 52;

	/**
	 * Renders the feedback as individual lines, without the {@code remote: } prefix.
	 *
	 * <p>
	 * Returned as lines rather than one blob because the transport writes them one at a
	 * time, and because it makes the output directly assertable in a test.
	 * @param feedback what to tell the student
	 * @return the lines to send, in order
	 */
	public List<String> render(PushFeedback feedback) {
		List<String> lines = new ArrayList<>();
		lines.add(feedback.productName());
		lines.add("");
		lines.add("Student:   " + feedback.studentName());
		lines.add("Assignment: " + feedback.assignmentTitle());
		lines.add("Commit:    " + feedback.shortCommitSha());
		lines.add("Signature: " + feedback.signatureBadge());
		lines.add("");

		switch (feedback.outcome()) {
			case GRADED -> appendScore(lines, feedback);
			case ACCEPTED_PENDING -> {
				lines.add("Submission accepted.");
				lines.add("The full check is running.");
			}
			case REJECTED -> appendRejection(lines, feedback);
			case INFRASTRUCTURE_ERROR -> {
				lines.add("Your submission was saved, but the checks could not be run.");
				lines.add("This is a problem on our side, not with your work.");
				lines.add("No attempt has been counted against you.");
			}
			default -> throw new IllegalStateException("Unhandled push outcome: " + feedback.outcome());
		}

		appendHints(lines, feedback);
		appendResultLink(lines, feedback);
		return List.copyOf(lines);
	}

	/**
	 * Renders the feedback as a single block, one line per row.
	 * @param feedback what to tell the student
	 * @return the rendered text
	 */
	public String renderText(PushFeedback feedback) {
		return String.join("\n", render(feedback));
	}

	private void appendScore(List<String> lines, PushFeedback feedback) {
		Integer passed = feedback.testsPassed();
		Integer total = feedback.testsTotal();
		if (passed != null && total != null) {
			lines.add(passed + " of " + total + " tests passed");
		}
		if (feedback.scorePercent() != null) {
			lines.add("Score: " + feedback.scorePercent() + " %");
		}
	}

	private void appendRejection(List<String> lines, PushFeedback feedback) {
		lines.add("Your push was rejected.");
		if (feedback.rejectionReason() != null) {
			lines.add("");
			lines.add(feedback.rejectionReason());
		}
	}

	private void appendHints(List<String> lines, PushFeedback feedback) {
		if (feedback.hints().isEmpty()) {
			return;
		}
		lines.add("");
		lines.add("Hints:");
		// Only manifest-authored hints reach this list. A raw assertion message from a
		// hidden test would leak its expected values, so the redactor upstream is what
		// guarantees these are safe - this method must never be given raw failure text.
		feedback.hints().forEach((hint) -> lines.add("  - " + hint));
	}

	private void appendResultLink(List<String> lines, PushFeedback feedback) {
		if (feedback.resultUrl() == null) {
			return;
		}
		lines.add("");
		lines.add("-".repeat(RULE_WIDTH));
		lines.add("Detailed result:");
		lines.add(feedback.resultUrl());
	}

}
