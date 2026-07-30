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

package org.gitgrader.submissions;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * The public API of the submissions module.
 *
 * <p>
 * Recording a submission is deliberately the only write operation offered. There is no
 * update and no delete: a submission is a historical fact, and the single mutation the
 * module permits - advancing the cached grading status - is expressed as
 * {@link #markStatus} so that it is obvious at every call site that nothing else about
 * the record can change.
 */
public interface SubmissionService {

	/**
	 * Records an accepted or refused push permanently.
	 *
	 * <p>
	 * Publishes {@link SubmissionRecorded} after the write commits, which is what the
	 * {@code grading} module listens for. The event is persisted by Spring Modulith's
	 * publication registry, so a crash between "push accepted" and "grading started"
	 * leaves the work recoverable on restart rather than silently lost.
	 * @param details everything known about the push
	 * @return the stored submission
	 */
	SubmissionView record(NewSubmission details);

	/**
	 * Advances the cached grading status of a submission.
	 * @param submissionId the submission
	 * @param status the new status
	 * @return the updated submission
	 */
	SubmissionView markStatus(UUID submissionId, SubmissionStatus status);

	/**
	 * Looks up one submission.
	 * @param submissionId the submission
	 * @return the submission, if it exists
	 */
	Optional<SubmissionView> findById(UUID submissionId);

	/**
	 * Lists a student's submissions for one assignment, newest first.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return the submission history
	 */
	List<SubmissionView> findHistory(UUID studentId, UUID assignmentId);

	/**
	 * Finds the newest submission a student made for an assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return the latest submission, if any
	 */
	Optional<SubmissionView> findLatest(UUID studentId, UUID assignmentId);

	/**
	 * Finds the newest submission a student made anywhere.
	 *
	 * <p>
	 * Backs {@code ssh git@host result latest}.
	 * @param studentId the student
	 * @return the latest submission, if any
	 */
	Optional<SubmissionView> findLatestForStudent(UUID studentId);

	/**
	 * Lists submissions in a course.
	 * @param courseId the course
	 * @param pageable page and ordering
	 * @return a page of submissions
	 */
	Page<SubmissionView> findByCourse(UUID courseId, Pageable pageable);

	/**
	 * Counts how many times a student submitted for an assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return the number of submissions
	 */
	long countAttempts(UUID studentId, UUID assignmentId);

}
