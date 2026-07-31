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
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.grading.GradingJobStatus;
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
	 * Claims the next runnable jobs for one worker, at most one per student.
	 *
	 * <p>
	 * A native query on purpose: {@code FOR UPDATE SKIP LOCKED} is what makes this a
	 * queue rather than a table everyone contends on. Without {@code SKIP LOCKED} two
	 * workers polling at the same moment would serialise behind the same row; with it,
	 * the second worker steps over the locked row and takes the next one. That single
	 * clause is the reason this platform needs no message broker.
	 *
	 * <p>
	 * <strong>Fairness.</strong> Plain FIFO let one student own the queue: a loop of
	 * pushes produced a job per push, and every one of them sat in front of the rest of
	 * the course. Two clauses fix that. {@code DISTINCT ON (student_id)} reduces the
	 * candidates to each student's oldest job, and the {@code NOT EXISTS} anti-join drops
	 * students who already occupy a worker. A student therefore holds at most one worker
	 * at a time however many assignments they have queued, and a backlog drains in
	 * round-robin order rather than in submission order.
	 *
	 * <p>
	 * <strong>Why this needs no extra lock.</strong> Two dispatchers running this
	 * concurrently both see the same committed rows, so {@code DISTINCT ON} picks the
	 * <em>same</em> head row for a given student; the loser's {@code SKIP LOCKED} steps
	 * over it and moves on to a different student instead of taking that student's second
	 * job. That holds only because a head is stable: {@code available_at} is set to the
	 * current instant on creation and moved further out on retry, so a newly queued job
	 * can never displace an older one at the front. Changing how {@code available_at} or
	 * {@code priority} are assigned would reopen the race this relies on being closed.
	 * @param now the current instant; jobs scheduled for later are skipped
	 * @param limit how many jobs to take at once
	 * @return ids of the claimed jobs, at most one per student
	 */
	@Query(value = """
			SELECT j.id FROM grading_jobs j
			WHERE j.id IN (
			        SELECT DISTINCT ON (c.student_id) c.id
			        FROM grading_jobs c
			        WHERE c.status = 'PENDING' AND c.available_at <= :now
			          AND NOT EXISTS (
			              SELECT 1 FROM grading_jobs busy
			              WHERE busy.student_id = c.student_id
			                AND busy.status IN ('CLAIMED', 'RUNNING'))
			        ORDER BY c.student_id, c.priority, c.available_at, c.id)
			  AND j.status = 'PENDING'
			ORDER BY j.priority, j.available_at, j.id
			LIMIT :limit
			FOR UPDATE SKIP LOCKED
			""", nativeQuery = true)
	List<UUID> claimNext(@Param("now") Instant now, @Param("limit") int limit);

	/**
	 * Serialises everything that follows in this transaction against one student and
	 * assignment.
	 *
	 * <p>
	 * Superseding is read-then-write: find the queued job, cancel it, queue the
	 * replacement. Two pushes arriving together would otherwise both read the same job to
	 * cancel and then both insert, and the partial unique index would turn the loser into
	 * a constraint violation rather than into a correct supersede. The lock is
	 * transaction scoped, so it is released on commit or rollback with no unlock path to
	 * forget.
	 * @param key identifies the student and assignment pair
	 */
	@Query(value = "SELECT pg_advisory_xact_lock(:key)", nativeQuery = true)
	void lockForEnqueue(@Param("key") long key);

	/**
	 * Finds the unstarted job a newer submission would supersede.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @param status the state to match, always {@code PENDING} in practice
	 * @return the queued job, if the student has one
	 */
	Optional<GradingJob> findByStudentIdAndAssignmentIdAndStatus(UUID studentId, UUID assignmentId,
			GradingJobStatus status);

	/**
	 * Counts a student's unstarted work in one course.
	 * @param studentId the student
	 * @param courseId the course
	 * @param status the state to count
	 * @return the number of queued jobs
	 */
	long countByStudentIdAndCourseIdAndStatus(UUID studentId, UUID courseId, GradingJobStatus status);

	/**
	 * Counts unstarted work in one course.
	 * @param courseId the course
	 * @param status the state to count
	 * @return the number of matching jobs
	 */
	long countByCourseIdAndStatus(UUID courseId, GradingJobStatus status);

	/**
	 * Returns jobs whose worker lease expired to the queue.
	 *
	 * <p>
	 * This is what makes a crashed worker recoverable. Without it a job claimed by a
	 * process that died would stay {@code CLAIMED} forever and the student would never
	 * get a result.
	 *
	 * <p>
	 * The attempt this job already consumed is deliberately <em>not</em> refunded here. A
	 * lease that ran out without an orderly shutdown may belong to a job that hangs its
	 * worker, and retrying that without bound would occupy a worker forever. An orderly
	 * shutdown returns its work through
	 * {@link org.gitgrader.grading.domain.GradingJob#requeueAfterShutdown} instead, which
	 * does refund.
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
	 * Finds the jobs one worker currently holds.
	 *
	 * <p>
	 * Used at shutdown to hand work back rather than leaving it to time out, which would
	 * otherwise strand every in-flight job for the length of the claim lease.
	 * @param worker the worker identifier
	 * @param statuses the states that count as held
	 * @return the jobs that worker holds
	 */
	List<GradingJob> findByClaimedByAndStatusIn(String worker, Collection<GradingJobStatus> statuses);

	/**
	 * Counts jobs in one state.
	 * @param status the state to count
	 * @return the number of matching jobs
	 */
	long countByStatus(GradingJobStatus status);

}
