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

import java.util.Optional;
import java.util.UUID;

/**
 * Asks for work to be graded again.
 *
 * <p>
 * Grading is normally driven by an accepted push, and nothing else needs to start it. A
 * regrade is the exception: an instructor re-runs a submission after fixing a broken test
 * suite or a runtime image, and the same queue, the same ceilings and the same coalescing
 * have to apply to it as to a push.
 *
 * <p>
 * Exposed from this module rather than reached into, because the submissions module
 * cannot depend on grading: grading already depends on submissions, and the reverse edge
 * would close a cycle. Callers therefore sit above both.
 */
public interface GradingCommands {

	/** Recorded on a run started by an instructor rather than by a push. */
	String MANUAL_RETRY = "MANUAL_RETRY";

	/**
	 * Queues a fresh grading run for a submission that already exists.
	 *
	 * <p>
	 * Supersedes any run for the same student and assignment that has not started, so
	 * pressing the button twice queues one run rather than two.
	 * @param submissionId the submission to grade again
	 * @return the queued run's identifier, or empty when a queue ceiling refused it
	 * @throws IllegalArgumentException when no such submission exists
	 */
	Optional<UUID> regrade(UUID submissionId);

}
