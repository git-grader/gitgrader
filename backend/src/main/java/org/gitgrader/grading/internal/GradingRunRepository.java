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

package org.gitgrader.grading.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.grading.domain.GradingRun;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access for grading runs.
 */
public interface GradingRunRepository extends JpaRepository<GradingRun, UUID> {

	/**
	 * Lists every attempt made against one submission, newest first.
	 *
	 * <p>
	 * Earlier attempts are never removed, so this is the audit trail behind a re-grade.
	 * @param submissionId the submission
	 * @return all runs for that submission
	 */
	List<GradingRun> findBySubmissionIdOrderByAttemptDesc(UUID submissionId);

	/**
	 * Finds the most recent attempt for a submission.
	 * @param submissionId the submission
	 * @return the newest run, if any
	 */
	Optional<GradingRun> findFirstBySubmissionIdOrderByAttemptDesc(UUID submissionId);

	/**
	 * Returns the next attempt number to use for a submission.
	 *
	 * <p>
	 * Computed in the database rather than by counting in Java, so two concurrent
	 * re-grade requests cannot both decide they are attempt two.
	 * @param submissionId the submission
	 * @return one more than the highest existing attempt, or 1 when there is none
	 */
	@Query("SELECT coalesce(max(r.attempt), 0) + 1 FROM GradingRun r WHERE r.submissionId = :submissionId")
	int nextAttempt(@Param("submissionId") UUID submissionId);

	/**
	 * Finds the run a given trigger already produced for a submission.
	 *
	 * <p>
	 * Asked before queueing a push, because Spring Modulith replays a publication that
	 * was never marked complete and the replay would otherwise queue the same push twice.
	 * Backed by {@code grading_runs_one_push_per_submission_idx}.
	 * @param submissionId the submission
	 * @param trigger what caused the run
	 * @return the existing run, if there is one
	 */
	Optional<GradingRun> findBySubmissionIdAndTrigger(UUID submissionId, String trigger);

}
