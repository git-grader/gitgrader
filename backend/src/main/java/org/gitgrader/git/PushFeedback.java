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

import org.jspecify.annotations.Nullable;

/**
 * Everything shown to a student in the output of their {@code git push}.
 *
 * <p>
 * Modelled as data rather than assembled as a string at the call site, so that the rules
 * about what a student may see are testable in isolation. In particular the hidden test
 * suite must never influence this object beyond a pass or fail count and a didactic hint.
 *
 * @param productName configured product name, shown as the banner
 * @param studentName display name of the pushing student
 * @param assignmentTitle the assignment answered
 * @param shortCommitSha abbreviated commit hash
 * @param signatureBadge {@code Verified} or {@code Unverified}
 * @param outcome what happened to the push
 * @param testsPassed number of tests passed, when grading already finished
 * @param testsTotal number of tests executed, when grading already finished
 * @param scorePercent formatted score such as {@code 70.0}, when grading already finished
 * @param resultUrl absolute, unguessable link to the full result
 * @param hints didactic hints, safe for a student to read
 * @param rejectionReason why the push was refused, when it was
 */
public record PushFeedback(String productName, String studentName, String assignmentTitle, String shortCommitSha,
		String signatureBadge, PushOutcome outcome, @Nullable Integer testsPassed, @Nullable Integer testsTotal,
		@Nullable String scorePercent, @Nullable String resultUrl, List<String> hints,
		@Nullable String rejectionReason) {

	public PushFeedback {
		hints = List.copyOf(hints);
	}

	/**
	 * What happened to the push, which decides the shape of the message.
	 */
	public enum PushOutcome {

		/** Graded synchronously; the score is already known. */
		GRADED,

		/** Accepted and queued; only the result link can be offered right now. */
		ACCEPTED_PENDING,

		/** Refused for a technical reason; nothing was recorded as an attempt. */
		REJECTED,

		/**
		 * Accepted, but grading could not run for a reason that is not the student's
		 * fault.
		 */
		INFRASTRUCTURE_ERROR

	}

}
