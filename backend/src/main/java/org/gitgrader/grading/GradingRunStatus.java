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
 * Lifecycle of one grading run, mirroring {@code grading_runs.status}.
 */
public enum GradingRunStatus {

	/** Waiting for a worker. */
	QUEUED,

	/** Executing in a sandbox. */
	RUNNING,

	/** Finished and produced a score, whatever that score was. */
	COMPLETED,

	/** The sandbox ran but could not be interpreted as a result. */
	FAILED,

	/** The sandbox exceeded its wall-clock limit and was killed. */
	TIMEOUT,

	/** Could not be carried out for a reason that is not the student's fault. */
	INFRASTRUCTURE_ERROR,

	/** Withdrawn, typically because a newer push superseded it. */
	CANCELLED;

	/**
	 * Whether the run produced a score a student may be graded on.
	 * @return true only for {@link #COMPLETED}
	 */
	public boolean isScored() {
		return this == COMPLETED;
	}

}
