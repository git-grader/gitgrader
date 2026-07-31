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

package org.gitgrader.submissions;

/**
 * Raised when a push was authenticated and well formed but must not become a submission.
 *
 * <p>
 * Carries a student-facing message on purpose. Unlike the public registration form, this
 * is only ever reached by an authenticated student acting on their own repository, so
 * telling them exactly which rule they hit reveals nothing they did not already know and
 * is the difference between a push they can correct and one that just fails.
 */
public class SubmissionRefusedException extends RuntimeException {

	private final Reason reason;

	public SubmissionRefusedException(Reason reason, String message) {
		super(message);
		this.reason = reason;
	}

	/**
	 * Why the push was refused.
	 * @return the reason
	 */
	public Reason reason() {
		return this.reason;
	}

	/** The distinct grounds on which a well formed push is still refused. */
	public enum Reason {

		/** This exact commit was already recorded for this repository. */
		DUPLICATE_COMMIT,

		/** The student exceeded their rolling push allowance for this assignment. */
		ASSIGNMENT_RATE_LIMIT,

		/** The student exceeded their rolling push allowance across all assignments. */
		STUDENT_RATE_LIMIT

	}

}
