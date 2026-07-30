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

package org.gitgrader.assignments;

import java.time.Instant;

/**
 * Result of evaluating a server receive time against assignment admission policy.
 *
 * @param outcome distinct acceptance or rejection outcome
 * @param late whether an accepted push arrived after its effective deadline
 * @param effectiveDueAt deadline used for this student
 */
public record AdmissionDecision(Outcome outcome, boolean late, Instant effectiveDueAt) {

	/**
	 * Reports whether admission succeeded.
	 * @return true only for accepted pushes
	 */
	public boolean accepted() {
		return this.outcome == Outcome.ACCEPTED;
	}

	/** Distinguishes every assignment admission outcome. */
	public enum Outcome {

		/** Push admitted. */
		ACCEPTED,
		/** Push arrived before the opening instant. */
		NOT_YET_OPEN,
		/** Push arrived after the effective deadline. */
		PAST_DEADLINE,
		/** Assignment was explicitly closed. */
		ASSIGNMENT_CLOSED,
		/** Assignment is historical. */
		ASSIGNMENT_ARCHIVED,
		/** Assignment has not been published. */
		ASSIGNMENT_DRAFT

	}

}
