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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.domain.Submission;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access for submissions.
 */
public interface SubmissionRepository extends JpaRepository<Submission, UUID> {

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
	 * Lists submissions in one status.
	 * @param status the status
	 * @return matching submissions
	 */
	List<Submission> findByStatus(SubmissionStatus status);

	/**
	 * Counts how many submissions a student made for an assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return the number of submissions
	 */
	long countByStudentIdAndAssignmentId(UUID studentId, UUID assignmentId);

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
