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
import java.util.Map;
import java.util.UUID;

import org.gitgrader.courses.EnrollmentStatus;
import org.gitgrader.grading.SubmissionScoreView;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.submissions.SubmissionStatus;
import org.jspecify.annotations.Nullable;

/** Course and class scoped progress for every enrolled student. */
public record ClassProgressReport(UUID courseId, UUID classId, String classKey, String className,
		int totalMandatoryAssignments, BigDecimal totalPointsAvailable, List<AssignmentSummary> assignments,
		List<StudentRow> students) {

	public ClassProgressReport {
		assignments = List.copyOf(assignments);
		students = List.copyOf(students);
	}

	/** Assignment-level result counts for the class. */
	public record AssignmentSummary(UUID assignmentId, String assignmentKey, String title, boolean mandatory,
			BigDecimal maxPoints, int testCount, int submissionCount, int passedCount, int failedCount,
			int infrastructureErrorCount, int notStartedCount, BigDecimal averagePercent) {
	}

	/** One student's enrollment and progress in this class. */
	public record StudentRow(UUID studentId, String studentUsername, String fullName, StudentStatus status,
			EnrollmentStatus enrollmentStatus, int fullyCompleted, int partiallyCompleted, int notStarted,
			BigDecimal completionRate, BigDecimal pointsEarned, BigDecimal pointsRate, BigDecimal totalPoints,
			long submissionCount, @Nullable Instant lastActivityAt, Map<String, AssignmentProgress> assignments) {

		public StudentRow {
			assignments = Map.copyOf(assignments);
		}

	}

	/** Best assignment progress plus the most recent submission and grading run. */
	public record AssignmentProgress(BigDecimal bestPercent, BigDecimal bestPoints,
			@Nullable LatestSubmission latestSubmission, @Nullable SubmissionScoreView latestGrading) {
	}

	/** Submission metadata available to instructors, without submitted source code. */
	public record LatestSubmission(UUID id, String commitSha, String gitRef, @Nullable String commitMessage,
			Instant receivedAt, SubmissionStatus status, boolean late) {
	}

}
