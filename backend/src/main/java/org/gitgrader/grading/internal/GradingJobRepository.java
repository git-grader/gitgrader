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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.gitgrader.grading.domain.GradingJob;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * The database-backed grading queue.
 */
public interface GradingJobRepository extends JpaRepository<GradingJob, UUID> {

	/**
	 * Claims the next runnable jobs for one worker.
	 *
	 * <p>
	 * A native query on purpose: {@code FOR UPDATE SKIP LOCKED} is what makes this a
	 * queue rather than a table everyone contends on. Without {@code SKIP LOCKED} two
	 * workers polling at the same moment would serialise behind the same row; with it,
	 * the second worker steps over the locked row and takes the next one. That single
	 * clause is the reason this platform needs no message broker.
	 *
	 * <p>
	 * The matching partial index ({@code grading_jobs_dispatch_idx}) covers exactly this
	 * predicate, so the scan stays small no matter how much completed history
	 * accumulates.
	 * @param now the current instant; jobs scheduled for later are skipped
	 * @param limit how many jobs to take at once
	 * @return ids of the claimed jobs
	 */
	@Query(value = """
			SELECT id FROM grading_jobs
			WHERE status = 'PENDING' AND available_at <= :now
			ORDER BY priority, available_at
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<UUID> claimNext(@Param("now") Instant now, @Param("limit") int limit);

	/**
	 * Returns jobs whose worker lease expired to the queue.
	 *
	 * <p>
	 * This is what makes a crashed worker recoverable. Without it a job claimed by a
	 * process that died would stay {@code CLAIMED} forever and the student would never
	 * get a result.
	 * @param now the current instant
	 * @return how many jobs were released
	 */
	@Modifying
	@Query(value = """
			UPDATE grading_jobs
			SET status = 'PENDING', claimed_at = NULL, claimed_by = NULL, claim_expires_at = NULL,
				updated_at = :now
			WHERE status IN ('CLAIMED', 'RUNNING') AND claim_expires_at < :now
			""", nativeQuery = true)
	int releaseExpiredClaims(@Param("now") Instant now);

	/**
	 * Counts jobs in one state.
	 * @param status the state to count
	 * @return the number of matching jobs
	 */
	long countByStatus(org.gitgrader.grading.GradingJobStatus status);

}
