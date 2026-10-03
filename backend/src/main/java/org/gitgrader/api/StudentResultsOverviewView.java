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

package org.gitgrader.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The results overview as the student's browser receives it.
 *
 * <p>
 * Mirrors {@code StudentResultsOverview} but adds the scoped link to each detailed
 * report, which only the HTTP layer is allowed to build. Hidden-check details are never
 * part of this payload; opening a report still applies the existing redaction.
 *
 * @param studentDisplayName the student's display name
 * @param generatedAt when the overview was assembled
 * @param courses the courses with at least one enrolled assignment
 */
public record StudentResultsOverviewView(String studentDisplayName, Instant generatedAt, List<CourseView> courses) {

	public StudentResultsOverviewView {
		courses = List.copyOf(courses);
	}

	/** One course the student is enrolled in. */
	public record CourseView(UUID courseId, String courseName, List<ClassView> classes) {

		public CourseView {
			classes = List.copyOf(classes);
		}

	}

	/**
	 * The student's class within a course, or a single unnamed group when none is set.
	 */
	public record ClassView(@Nullable UUID classId, @Nullable String className, List<AssignmentView> assignments) {

		public ClassView {
			assignments = List.copyOf(assignments);
		}

	}

	/** The student's standing on one assignment. */
	public record AssignmentView(UUID assignmentId, String assignmentKey, String title, @Nullable AttemptView latest) {
	}

	/** The most recent attempt and its latest grading outcome, plus its report link. */
	public record AttemptView(UUID submissionId, String shortCommitSha, Instant receivedAt, boolean late,
			String submissionStatus, @Nullable String gradingStatus, @Nullable Integer testsPassed,
			@Nullable Integer testsTotal, @Nullable BigDecimal scorePercent, @Nullable Boolean passed,
			String resultUrl) {
	}

}
