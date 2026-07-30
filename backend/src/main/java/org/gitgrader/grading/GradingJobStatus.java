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
 * Lifecycle of one queued grading job.
 */
public enum GradingJobStatus {

	/** Waiting for a worker. */
	PENDING,

	/** Claimed by a worker but not started yet. */
	CLAIMED,

	/** Executing in a sandbox right now. */
	RUNNING,

	/** Finished, whatever the score was. */
	DONE,

	/** Gave up after exhausting its attempts. */
	FAILED,

	/** Withdrawn, typically because a newer push superseded it. */
	CANCELLED;

	/**
	 * Whether a worker currently holds this job.
	 *
	 * <p>
	 * Used by the reaper: a job in one of these states whose claim has expired belongs to
	 * a worker that died, and has to go back on the queue rather than sit there forever.
	 * @return true when the job is claimed or running
	 */
	public boolean isClaimed() {
		return this == CLAIMED || this == RUNNING;
	}

}
