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
import java.util.List;
import java.util.UUID;

import org.gitgrader.grading.InstructorGradingResult;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.reports.ClassProgressReport.LatestSubmission;
import org.jspecify.annotations.Nullable;

/**
 * Course-context student progress and latest assignment attempts.
 *
 * @param courseId course the class belongs to
 * @param classId class the student is enrolled in
 * @param studentId student the detail is about
 * @param studentUsername login name of the student
 * @param fullName display name of the student
 * @param status account status of the student
 * @param assignments one entry per assignment visible in the class
 */
public record ClassStudentDetail(UUID courseId, UUID classId, UUID studentId, String studentUsername, String fullName,
		StudentStatus status, List<AssignmentDetail> assignments) {

	public ClassStudentDetail {
		assignments = List.copyOf(assignments);
	}

	/** Best progress and safe latest attempt details for one assignment. */
	public record AssignmentDetail(UUID assignmentId, String assignmentKey, String title, BigDecimal bestPercent,
			BigDecimal bestPoints, @Nullable LatestSubmission latestSubmission,
			@Nullable InstructorGradingResult latestGrading) {
	}

}
