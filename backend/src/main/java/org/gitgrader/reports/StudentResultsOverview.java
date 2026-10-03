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

package org.gitgrader.reports;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One student's results across every course they are enrolled in.
 *
 * <p>
 * Grouped by course and class, and within that by assignment, each assignment carrying
 * only its most recent graded attempt. This is the student-facing counterpart of the
 * instructor progress reports: it names courses and assignments but never a hidden check.
 *
 * @param studentDisplayName the student's display name
 * @param generatedAt when the overview was assembled
 * @param courses the courses with at least one enrolled assignment
 */
public record StudentResultsOverview(String studentDisplayName, Instant generatedAt, List<CourseGroup> courses) {

	public StudentResultsOverview {
		courses = List.copyOf(courses);
	}

	/**
	 * One course the student is enrolled in.
	 *
	 * @param courseId course identifier
	 * @param courseName course display name
	 * @param classes the student's classes in this course
	 */
	public record CourseGroup(UUID courseId, String courseName, List<ClassGroup> classes) {

		public CourseGroup {
			classes = List.copyOf(classes);
		}

	}

	/**
	 * The student's class within a course, or a single unnamed group when none is set.
	 *
	 * @param classId class identifier, absent when the student is enrolled at course
	 * level
	 * @param className class display name, absent when the student has no class
	 * @param assignments the assignments in this course
	 */
	public record ClassGroup(@Nullable UUID classId, @Nullable String className, List<AssignmentResult> assignments) {

		public ClassGroup {
			assignments = List.copyOf(assignments);
		}

	}

	/**
	 * The student's standing on one assignment.
	 *
	 * @param assignmentId assignment identifier
	 * @param assignmentKey course-local key
	 * @param title assignment title
	 * @param latest the most recent attempt, absent when the student never pushed
	 */
	public record AssignmentResult(UUID assignmentId, String assignmentKey, String title,
			@Nullable LatestAttempt latest) {
	}

	/**
	 * The most recent attempt at an assignment and its latest grading outcome.
	 *
	 * @param submissionId submission identifier
	 * @param shortCommitSha abbreviated commit hash
	 * @param receivedAt when the push was accepted
	 * @param late whether it arrived after the effective deadline
	 * @param submissionStatus current submission lifecycle state
	 * @param gradingStatus latest grading run state, absent when nothing was graded
	 * @param testsPassed checks passed, absent when the run produced no result
	 * @param testsTotal checks the suite declares, absent when the run produced no result
	 * @param scorePercent recorded percentage, absent when the run produced no result
	 * @param passed whether the run met the pass threshold, absent when nothing was
	 * graded
	 */
	public record LatestAttempt(UUID submissionId, String shortCommitSha, Instant receivedAt, boolean late,
			String submissionStatus, @Nullable String gradingStatus, @Nullable Integer testsPassed,
			@Nullable Integer testsTotal, @Nullable BigDecimal scorePercent, @Nullable Boolean passed) {
	}

}
