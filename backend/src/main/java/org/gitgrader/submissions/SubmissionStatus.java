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
 * Lifecycle of a submission, mirroring the {@code submissions.status} column.
 *
 * <p>
 * These values describe what the platform did with a push. They are not a grade: a
 * submission that reaches {@link #FAILED} was processed perfectly, the student's tests
 * simply did not all pass.
 */
public enum SubmissionStatus {

	/** The push was accepted and persisted; grading has not been scheduled yet. */
	RECEIVED,

	/** A grading run is waiting for a free worker. */
	QUEUED,

	/** A grading run is executing right now. */
	RUNNING,

	/** Grading finished and the score met the assignment's pass threshold. */
	PASSED,

	/** Grading finished and the score did not meet the pass threshold. */
	FAILED,

	/**
	 * Grading could not be carried out for a reason that is not the student's fault.
	 *
	 * <p>
	 * Kept strictly separate from {@link #FAILED}. An image that would not pull or a
	 * sandbox that ran out of memory says nothing about the submitted work, and reporting
	 * it as a failed attempt would be an unfair grade.
	 */
	INFRASTRUCTURE_ERROR,

	/** Grading was cancelled, typically because a newer push superseded this one. */
	CANCELLED,

	/**
	 * The push was refused before it could become a submission.
	 *
	 * <p>
	 * Reserved for technical admission failures such as an unsigned commit or a closed
	 * assignment. The reason is always recorded alongside.
	 */
	REJECTED;

	/**
	 * Whether grading has finished, for any reason.
	 * @return true when no further processing is expected
	 */
	public boolean isTerminal() {
		return this == PASSED || this == FAILED || this == INFRASTRUCTURE_ERROR || this == CANCELLED
				|| this == REJECTED;
	}

	/**
	 * Whether this status reflects a real assessment of the submitted work.
	 *
	 * <p>
	 * Reporting relies on this to avoid counting an infrastructure failure as an attempt.
	 * @return true only when the outcome is a genuine pass or fail
	 */
	public boolean isGraded() {
		return this == PASSED || this == FAILED;
	}

}
