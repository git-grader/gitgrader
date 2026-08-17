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

package org.gitgrader.submissions.internal;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionAssessmentView;
import org.gitgrader.submissions.domain.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access for submissions.
 */
public interface SubmissionRepository extends JpaRepository<Submission, UUID>, JpaSpecificationExecutor<Submission> {

	/**
	 * Lists a student's submissions for one assignment, newest first.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return matching submissions
	 */
	List<Submission> findByStudentIdAndAssignmentIdOrderByReceivedAtDesc(UUID studentId, UUID assignmentId);

	/**
	 * Finds the most recent submission a student made for an assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return the newest submission, if any
	 */
	Optional<Submission> findFirstByStudentIdAndAssignmentIdOrderByReceivedAtDesc(UUID studentId, UUID assignmentId);

	/**
	 * Finds the most recent submission a student made anywhere.
	 * @param studentId the student
	 * @return the newest submission, if any
	 */
	Optional<Submission> findFirstByStudentIdOrderByReceivedAtDesc(UUID studentId);

	/**
	 * Lists every submission in a course, newest first.
	 * @param courseId the course
	 * @param pageable page and ordering
	 * @return a page of submissions
	 */
	Page<Submission> findByCourseId(UUID courseId, Pageable pageable);

	/**
	 * Counts submissions in one status.
	 * @param status the status
	 * @return number of matching submissions
	 */
	long countByStatus(SubmissionStatus status);

	/**
	 * Counts how many submissions a student made for an assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return the number of submissions
	 */
	long countByStudentIdAndAssignmentId(UUID studentId, UUID assignmentId);

	/**
	 * Serialises admission decisions for one repository.
	 *
	 * <p>
	 * The duplicate and rolling-window checks are read-then-write. Two pushes arriving
	 * together would otherwise both read a count below the limit and both insert, letting
	 * a burst step straight over the ceiling. The lock is transaction scoped, so it is
	 * released on commit or rollback with no unlock path to forget.
	 * @param key identifies the repository
	 */
	@Query(value = "SELECT pg_advisory_xact_lock(:key)", nativeQuery = true)
	void lockForAdmission(@Param("key") long key);

	/**
	 * Reports whether this exact commit was already submitted to this repository.
	 *
	 * <p>
	 * Answered from the leading columns of {@code submissions_unique_commit}, which is a
	 * btree on (repository_id, commit_sha, received_at). That constraint does not prevent
	 * a duplicate on its own, because it includes the receive time: the same commit
	 * pushed twice a second apart produces two distinct keys.
	 * @param repositoryId the repository pushed to
	 * @param commitSha the commit at the branch tip
	 * @return true when an earlier submission already recorded this commit
	 */
	boolean existsByRepositoryIdAndCommitSha(UUID repositoryId, String commitSha);

	/**
	 * Counts a student's recent pushes to one assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @param since start of the rolling window
	 * @return the number of submissions inside the window
	 */
	long countByStudentIdAndAssignmentIdAndReceivedAtAfter(UUID studentId, UUID assignmentId, Instant since);

	/**
	 * Counts a student's recent pushes across every assignment.
	 * @param studentId the student
	 * @param since start of the rolling window
	 * @return the number of submissions inside the window
	 */
	long countByStudentIdAndReceivedAtAfter(UUID studentId, Instant since);

	@Query("""
			SELECT new org.gitgrader.submissions.SubmissionAssessmentView(
				s.id, s.studentId, s.assignmentId, s.status, s.receivedAt)
			FROM Submission s
			WHERE s.courseId = :courseId AND s.assignmentId IN :assignmentIds
			""")
	List<SubmissionAssessmentView> findAssessments(@Param("courseId") UUID courseId,
			@Param("assignmentIds") Collection<UUID> assignmentIds);

	/**
	 * Counts submissions in a course grouped by status.
	 *
	 * <p>
	 * Used by the dashboard, which would otherwise load every row just to count them.
	 * @param courseId the course
	 * @param status the status to count
	 * @return the number of matching submissions
	 */
	@Query("SELECT count(s) FROM Submission s WHERE s.courseId = :courseId AND s.status = :status")
	long countByCourseAndStatus(@Param("courseId") UUID courseId, @Param("status") SubmissionStatus status);

}
