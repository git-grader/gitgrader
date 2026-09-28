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
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.EntityNotFoundException;
import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseClassView;
import org.gitgrader.courses.EnrollmentView;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.GradingResultQuery;
import org.gitgrader.grading.InstructorGradingResult;
import org.gitgrader.grading.SubmissionScoreView;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentView;
import org.gitgrader.submissions.SubmissionAssessmentView;
import org.gitgrader.submissions.SubmissionService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Assembles course/class-scoped progress and safe student grading details. */
@Service
@Transactional(readOnly = true)
class ClassReportService {

	private final CourseCatalog courses;

	private final AssignmentCatalog assignments;

	private final StudentDirectory students;

	private final SubmissionService submissions;

	private final GradingResultQuery gradingResults;

	ClassReportService(CourseCatalog courses, AssignmentCatalog assignments, StudentDirectory students,
			SubmissionService submissions, GradingResultQuery gradingResults) {
		this.courses = courses;
		this.assignments = assignments;
		this.students = students;
		this.submissions = submissions;
		this.gradingResults = gradingResults;
	}

	ClassProgressReport report(UUID courseId, UUID classId) {
		ClassData data = load(courseId, classId);
		Map<UUID, StudentProgressRow> progress = progressByStudent(data);
		Map<StudentAssignment, SubmissionAssessmentView> latest = latestByStudentAssignment(data.assessments());
		return new ClassProgressReport(courseId, classId, data.courseClass().classKey(), data.courseClass().name(),
				mandatoryCount(data.assignments()), pointsAvailable(data.assignments()),
				assignmentSummaries(data, latest), studentRows(data, progress, latest));
	}

	ClassStudentDetail studentDetail(UUID courseId, UUID classId, UUID studentId) {
		ClassProgressReport report = report(courseId, classId);
		ClassProgressReport.StudentRow student = report.students()
			.stream()
			.filter((row) -> row.studentId().equals(studentId))
			.findFirst()
			.orElseThrow(() -> new EntityNotFoundException("Student not found in course class"));
		List<ClassStudentDetail.AssignmentDetail> details = this.assignments.findByCourse(courseId)
			.stream()
			.map((assignment) -> assignmentDetail(student, assignment))
			.toList();
		return new ClassStudentDetail(courseId, classId, studentId, student.studentUsername(), student.fullName(),
				student.status(), details);
	}

	private ClassData load(UUID courseId, UUID classId) {
		this.courses.findCourse(courseId).orElseThrow(() -> new EntityNotFoundException("Course not found"));
		CourseClassView courseClass = this.courses.findClasses(courseId)
			.stream()
			.filter((item) -> item.id().equals(classId))
			.findFirst()
			.orElseThrow(() -> new EntityNotFoundException("Course class not found"));
		List<AssignmentView> assignments = this.assignments.findByCourse(courseId);
		List<EnrollmentView> enrollments = this.courses.findClassEnrollments(courseId, classId);
		List<StudentView> students = this.students
			.findByIds(enrollments.stream().map(EnrollmentView::studentId).toList());
		List<SubmissionAssessmentView> assessments = assignments.isEmpty() ? List.of()
				: this.submissions.findAssessments(courseId, assignments.stream().map(AssignmentView::id).toList());
		Map<UUID, SubmissionScoreView> latestScores = this.gradingResults
			.findLatestScores(assessments.stream().map(SubmissionAssessmentView::submissionId).toList())
			.stream()
			.collect(Collectors.toMap(SubmissionScoreView::submissionId, (score) -> score));
		return new ClassData(courseClass, assignments, enrollments, students, assessments, latestScores);
	}

	private Map<UUID, StudentProgressRow> progressByStudent(ClassData data) {
		Map<UUID, BigDecimal> scores = data.latestScores()
			.entrySet()
			.stream()
			.filter((entry) -> entry.getValue().status().isScored())
			.collect(Collectors.toMap(Map.Entry::getKey, (entry) -> entry.getValue().scorePercent() == null
					? BigDecimal.ZERO : entry.getValue().scorePercent()));
		return data.students()
			.stream()
			.map((student) -> StudentProgressRows.of(student, data.assignments(), data.assessments(), scores))
			.collect(Collectors.toMap(StudentProgressRow::studentId, (row) -> row));
	}

	private List<ClassProgressReport.StudentRow> studentRows(ClassData data, Map<UUID, StudentProgressRow> progress,
			Map<StudentAssignment, SubmissionAssessmentView> latest) {
		Map<UUID, EnrollmentView> enrollmentByStudent = data.enrollments()
			.stream()
			.collect(Collectors.toMap(EnrollmentView::studentId, (enrollment) -> enrollment));
		return data.students().stream().map((student) -> {
			StudentProgressRow row = progress.get(student.id());
			Map<String, ClassProgressReport.AssignmentProgress> assignments = new HashMap<>();
			for (AssignmentView assignment : data.assignments()) {
				StudentProgressRow.AssignmentProgress best = row.assignments().get(assignment.assignmentKey());
				SubmissionAssessmentView newest = latest.get(new StudentAssignment(student.id(), assignment.id()));
				SubmissionScoreView grading = newest == null ? null : data.latestScores().get(newest.submissionId());
				assignments.put(assignment.assignmentKey(), new ClassProgressReport.AssignmentProgress(best.percent(),
						best.points(), latestSubmission(newest), grading));
			}
			long submissionCount = data.assessments()
				.stream()
				.filter((assessment) -> assessment.studentId().equals(student.id()))
				.count();
			return new ClassProgressReport.StudentRow(student.id(), student.studentUsername(), student.fullName(),
					student.status(), enrollmentByStudent.get(student.id()).status(), row.fullyCompleted(),
					row.partiallyCompleted(), row.notStarted(), row.completionRate(), row.pointsEarned(),
					row.pointsRate(), row.totalPoints(), submissionCount, row.lastActivityAt(), assignments);
		}).toList();
	}

	private List<ClassProgressReport.AssignmentSummary> assignmentSummaries(ClassData data,
			Map<StudentAssignment, SubmissionAssessmentView> latest) {
		return data.assignments().stream().map((assignment) -> {
			int submissions = 0;
			int passed = 0;
			int failed = 0;
			int infrastructureErrors = 0;
			int notStarted = 0;
			int graded = 0;
			BigDecimal percentTotal = BigDecimal.ZERO;
			for (StudentView student : data.students()) {
				SubmissionAssessmentView newest = latest.get(new StudentAssignment(student.id(), assignment.id()));
				if (newest == null) {
					notStarted++;
					continue;
				}
				submissions++;
				switch (newest.status()) {
					case PASSED -> passed++;
					case FAILED -> failed++;
					case INFRASTRUCTURE_ERROR -> infrastructureErrors++;
					default -> {
					}
				}
				SubmissionScoreView score = data.latestScores().get(newest.submissionId());
				BigDecimal percent = (score == null) ? null : score.scorePercent();
				if (score != null && score.status() == GradingRunStatus.COMPLETED && percent != null) {
					percentTotal = percentTotal.add(percent);
					graded++;
				}
			}
			BigDecimal average = graded == 0 ? BigDecimal.ZERO
					: percentTotal.divide(BigDecimal.valueOf(graded), 2, RoundingMode.HALF_UP);
			return new ClassProgressReport.AssignmentSummary(assignment.id(), assignment.assignmentKey(),
					assignment.title(), assignment.mandatory(), assignment.maxPoints(), assignment.testCount(),
					submissions, passed, failed, infrastructureErrors, notStarted, average);
		}).toList();
	}

	private ClassStudentDetail.AssignmentDetail assignmentDetail(ClassProgressReport.StudentRow student,
			AssignmentView assignment) {
		ClassProgressReport.AssignmentProgress progress = student.assignments().get(assignment.assignmentKey());
		ClassProgressReport.LatestSubmission latest = progress.latestSubmission();
		InstructorGradingResult grading = latest == null ? null
				: this.gradingResults.findLatestInstructorResultForSubmission(latest.id()).orElse(null);
		return new ClassStudentDetail.AssignmentDetail(assignment.id(), assignment.assignmentKey(), assignment.title(),
				progress.bestPercent(), progress.bestPoints(), latest, grading);
	}

	private static Map<StudentAssignment, SubmissionAssessmentView> latestByStudentAssignment(
			List<SubmissionAssessmentView> assessments) {
		Map<StudentAssignment, SubmissionAssessmentView> latest = new HashMap<>();
		for (SubmissionAssessmentView assessment : assessments) {
			StudentAssignment key = new StudentAssignment(assessment.studentId(), assessment.assignmentId());
			latest.merge(key, assessment,
					(current, candidate) -> candidate.receivedAt().isAfter(current.receivedAt()) ? candidate : current);
		}
		return latest;
	}

	private static ClassProgressReport.LatestSubmission latestSubmission(SubmissionAssessmentView assessment) {
		return assessment == null ? null
				: new ClassProgressReport.LatestSubmission(assessment.submissionId(), assessment.commitSha(),
						assessment.gitRef(), assessment.commitMessage(), assessment.receivedAt(), assessment.status(),
						assessment.late());
	}

	private static int mandatoryCount(List<AssignmentView> assignments) {
		return (int) assignments.stream().filter(AssignmentView::mandatory).count();
	}

	private static BigDecimal pointsAvailable(List<AssignmentView> assignments) {
		return assignments.stream().map(AssignmentView::maxPoints).reduce(BigDecimal.ZERO, BigDecimal::add);
	}

	private record StudentAssignment(UUID studentId, UUID assignmentId) {
	}

	private record ClassData(CourseClassView courseClass, List<AssignmentView> assignments,
			List<EnrollmentView> enrollments, List<StudentView> students, List<SubmissionAssessmentView> assessments,
			Map<UUID, SubmissionScoreView> latestScores) {
	}

}
