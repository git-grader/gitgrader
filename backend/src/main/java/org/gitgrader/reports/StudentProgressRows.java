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
import java.util.Map;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.identity.StudentView;
import org.gitgrader.submissions.SubmissionAssessmentView;

/**
 * Adapts the course scoped view types onto {@link ReportCalculator}'s own input records.
 *
 * Both the course report and the class report derive a student progress row from the same
 * facts, so the translation lives here rather than in each caller.
 */
final class StudentProgressRows {

	private StudentProgressRows() {
	}

	/**
	 * Calculates one student's progress row.
	 * @param student the student to calculate for
	 * @param assignments assignments of the course
	 * @param assessments every assessment of the course
	 * @param scores latest score per graded submission
	 * @return the calculated progress row
	 */
	static StudentProgressRow of(StudentView student, List<AssignmentView> assignments,
			List<SubmissionAssessmentView> assessments, Map<UUID, BigDecimal> scores) {
		List<ReportCalculator.Assignment> definitions = assignments.stream()
			.map((item) -> new ReportCalculator.Assignment(item.id(), item.assignmentKey(), item.mandatory(),
					item.maxPoints(), item.passThreshold()))
			.toList();
		List<ReportCalculator.Assessment> studentAssessments = assessments.stream()
			.filter((assessment) -> assessment.studentId().equals(student.id()))
			.map((assessment) -> new ReportCalculator.Assessment(assessment.assignmentId(), assessment.status(),
					assessment.status().isGraded() ? scores.getOrDefault(assessment.submissionId(), BigDecimal.ZERO)
							: null,
					assessment.receivedAt()))
			.toList();
		return ReportCalculator.calculate(
				new ReportCalculator.Student(student.id(), student.studentUsername(), student.fullName()), definitions,
				studentAssessments);
	}

}
