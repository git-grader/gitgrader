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

package org.gitgrader.grading;

import org.gitgrader.grading.internal.ParsedResult;

/**
 * Enforces confidentiality of hidden test suites.
 */
public final class TestResultRedactor {

	private TestResultRedactor() {
		// Utility class
	}

	/**
	 * Creates a safe student view from a parsed result.
	 * @param result the parsed test result
	 * @return a view structurally incapable of carrying hidden secrets
	 */
	public static StudentTestResultView toStudentView(ParsedResult result) {
		if ("HIDDEN".equals(result.visibility())) {
			return new StudentTestResultView("HIDDEN", result.category(), null, // publicName
																				// not
																				// shown
																				// or used
																				// for
																				// hidden
																				// tests
																				// typically,
																				// or it
																				// is just
																				// a
																				// category
					result.outcome(), result.durationMs(), null, // studentMessage is null
																	// to hide assertions
					result.hint());
		}
		else {
			return new StudentTestResultView(result.visibility(), result.category(),
					result.publicName() != null ? result.publicName() : result.testName(), result.outcome(),
					result.durationMs(),
					result.studentMessage() != null ? result.studentMessage() : result.internalMessage(),
					result.hint());
		}
	}

	/**
	 * Creates a full instructor view from a parsed result.
	 * @param result the parsed test result
	 * @return a view with all details
	 */
	public static InstructorTestResultView toInstructorView(ParsedResult result) {
		return new InstructorTestResultView(result.visibility(), result.category(), result.testName(),
				result.publicName(), result.outcome(), result.durationMs(), result.studentMessage(),
				result.internalMessage(), result.hint());
	}

	/**
	 * Checks if a log stream is safe for students to see.
	 *
	 * <p>
	 * Grading logs echo hidden test content, so they must be instructor-only.
	 * @return false always
	 */
	public static boolean isLogSafeForStudent() {
		return false;
	}

}
