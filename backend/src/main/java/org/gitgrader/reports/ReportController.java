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

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.EnrollmentView;
import org.gitgrader.grading.GradingResultQuery;
import org.gitgrader.grading.StudentGradingResult;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentView;
import org.gitgrader.submissions.SubmissionService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Serves read-only course reports and file exports. */
@RestController
@RequestMapping("/api/v1/reports/courses")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class ReportController {

	private final CourseCatalog courses;

	private final AssignmentCatalog assignments;

	private final StudentDirectory students;

	private final SubmissionService submissions;

	private final GradingResultQuery gradingResults;

	private final ReportExportService exports;

	public ReportController(CourseCatalog courses, AssignmentCatalog assignments, StudentDirectory students,
			SubmissionService submissions, GradingResultQuery gradingResults, ReportExportService exports) {
		this.courses = courses;
		this.assignments = assignments;
		this.students = students;
		this.submissions = submissions;
		this.gradingResults = gradingResults;
		this.exports = exports;
	}

	@GetMapping("/{courseId}")
	public CourseReport report(@PathVariable UUID courseId) {
		this.courses.findCourse(courseId).orElseThrow(() -> new IllegalArgumentException("Course not found"));
		List<AssignmentView> courseAssignments = this.assignments.findByCourse(courseId);
		List<StudentProgressRow> rows = this.students
			.search(new org.gitgrader.identity.StudentSearch(null, null, null), Pageable.unpaged())
			.getContent()
			.stream()
			.filter((student) -> enrolledIn(student.id(), courseId))
			.map((student) -> calculate(student, courseAssignments))
			.toList();
		int mandatory = (int) courseAssignments.stream().filter(AssignmentView::mandatory).count();
		BigDecimal points = courseAssignments.stream()
			.map(AssignmentView::maxPoints)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		return new CourseReport(courseId, mandatory, points, rows);
	}

	@GetMapping("/{courseId}/export")
	public ResponseEntity<ByteArrayResource> export(@PathVariable UUID courseId, @RequestParam String format)
			throws IOException {
		ReportFormat representation = ReportFormat.parse(format);
		byte[] content = this.exports.export(report(courseId), representation);
		HttpHeaders headers = new HttpHeaders();
		headers.setContentType(MediaType.parseMediaType(representation.mediaType()));
		headers.setContentDisposition(
				ContentDisposition.attachment().filename("course-report." + representation.extension()).build());
		return ResponseEntity.ok().headers(headers).body(new ByteArrayResource(content));
	}

	private boolean enrolledIn(UUID studentId, UUID courseId) {
		return this.courses.findEnrollments(studentId)
			.stream()
			.map(EnrollmentView::courseId)
			.anyMatch(courseId::equals);
	}

	private StudentProgressRow calculate(StudentView student, List<AssignmentView> assignments) {
		List<ReportCalculator.Assignment> definitions = assignments.stream()
			.map((item) -> new ReportCalculator.Assignment(item.id(), item.assignmentKey(), item.mandatory(),
					item.maxPoints(), item.passThreshold()))
			.toList();
		List<ReportCalculator.Assessment> assessments = assignments.stream()
			.flatMap((assignment) -> this.submissions.findHistory(student.id(), assignment.id()).stream())
			.map((submission) -> new ReportCalculator.Assessment(submission.assignmentId(), submission.status(),
					submission.status().isGraded() ? scoreOf(submission.id()) : null, submission.receivedAt()))
			.toList();
		return ReportCalculator.calculate(
				new ReportCalculator.Student(student.id(), student.studentNumber(), student.fullName()), definitions,
				assessments);
	}

	/**
	 * The percentage a graded submission actually earned.
	 *
	 * <p>
	 * Read from the run rather than inferred from the submission's status. A status only
	 * says whether the run cleared the assignment's pass threshold, so deriving a
	 * percentage from it awards all of an assignment's points or none of them: 70 against
	 * a threshold of 80 is FAILED, and reporting that as zero understates both the
	 * student's points and the course's points rate.
	 * @param submissionId the graded submission
	 * @return the recorded percentage, or zero when no run recorded one
	 */
	private BigDecimal scoreOf(UUID submissionId) {
		return this.gradingResults.findLatestForSubmission(submissionId)
			.map(StudentGradingResult::scorePercent)
			.orElse(BigDecimal.ZERO);
	}

}
