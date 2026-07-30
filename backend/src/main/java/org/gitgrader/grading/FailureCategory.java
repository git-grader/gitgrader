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

/**
 * Why a grading run did not end in a pass.
 *
 * <p>
 * The whole point of this enum is to keep the first constant apart from the rest. Only
 * {@link #STUDENT_TEST_FAILURE} says anything about the submitted work; every other value
 * is the platform's own problem and must never be presented to a student as a grade, or
 * counted as a used attempt.
 */
public enum FailureCategory {

	/** The student's code did not pass the tests. This is a real result. */
	STUDENT_TEST_FAILURE,

	/** The push was structurally unacceptable, for example unsigned. */
	INVALID_SUBMISSION,

	/** The sandbox itself misbehaved: image pull, mount, or start failure. */
	RUNNER_ERROR,

	/** Something outside the runner failed: database, storage, or network. */
	INFRASTRUCTURE_ERROR,

	/** A defect in GitGrader itself. */
	INTERNAL_ERROR;

	/**
	 * Whether this failure reflects the submitted work.
	 * @return true only for a genuine test failure
	 */
	public boolean isStudentFault() {
		return this == STUDENT_TEST_FAILURE;
	}

}
