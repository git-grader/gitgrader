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

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseClassView;
import org.gitgrader.courses.CourseView;
import org.gitgrader.courses.EnrollmentView;
import org.gitgrader.grading.GradingResultQuery;
import org.gitgrader.grading.SubmissionScoreView;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentView;
import org.gitgrader.reports.StudentResultsOverview.AssignmentResult;
import org.gitgrader.reports.StudentResultsOverview.ClassGroup;
import org.gitgrader.reports.StudentResultsOverview.CourseGroup;
import org.gitgrader.reports.StudentResultsOverview.LatestAttempt;
import org.gitgrader.submissions.SubmissionAssessmentView;
import org.gitgrader.submissions.SubmissionService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link StudentResultsOverviewQuery}.
 *
 * <p>
 * Reads only columns a student is allowed to see. Like the instructor progress report it
 * is assembled from the newest submission per assignment and that submission's latest
 * grading run, but it deliberately never resolves individual test results: the detailed,
 * still-redacted breakdown stays behind the per-submission result page.
 */
@Service
@Transactional(readOnly = true)
class DefaultStudentResultsOverviewQuery implements StudentResultsOverviewQuery {

	/** Hash characters shown to a student, matching git's default abbreviation. */
	private static final int SHORT_SHA_LENGTH = 7;

	private final CourseCatalog courses;

	private final AssignmentCatalog assignments;

	private final StudentDirectory students;

	private final SubmissionService submissions;

	private final GradingResultQuery gradingResults;

	private final Clock clock;

	DefaultStudentResultsOverviewQuery(CourseCatalog courses, AssignmentCatalog assignments, StudentDirectory students,
			SubmissionService submissions, GradingResultQuery gradingResults, Clock clock) {
		this.courses = courses;
		this.assignments = assignments;
		this.students = students;
		this.submissions = submissions;
		this.gradingResults = gradingResults;
		this.clock = clock;
	}

	@Override
	public Optional<StudentResultsOverview> forStudent(UUID studentId) {
		Optional<StudentView> maybeStudent = this.students.findById(studentId);
		if (maybeStudent.isEmpty()) {
			return Optional.empty();
		}
		StudentView student = maybeStudent.get();

		Map<UUID, UUID> classByCourse = classByCourse(this.courses.findEnrollments(studentId));
		List<CourseGroup> groups = new ArrayList<>();
		for (UUID courseId : classByCourse.keySet()) {
			this.courses.findCourse(courseId)
				.map((course) -> courseGroup(course, studentId, classByCourse.get(courseId)))
				.ifPresent(groups::add);
		}
		groups.sort(Comparator.comparing(CourseGroup::courseName, String.CASE_INSENSITIVE_ORDER));
		return Optional.of(new StudentResultsOverview(student.fullName(), Instant.now(this.clock), groups));
	}

	private CourseGroup courseGroup(CourseView course, UUID studentId, @Nullable UUID classId) {
		List<AssignmentView> courseAssignments = this.assignments.findByCourse(course.id());
		List<UUID> assignmentIds = courseAssignments.stream().map(AssignmentView::id).toList();
		List<SubmissionAssessmentView> assessments = this.submissions.findAssessmentsForStudent(course.id(), studentId,
				assignmentIds);

		Map<UUID, SubmissionAssessmentView> latestByAssignment = new LinkedHashMap<>();
		for (SubmissionAssessmentView assessment : assessments) {
			latestByAssignment.merge(assessment.assignmentId(), assessment,
					(current, candidate) -> candidate.receivedAt().isAfter(current.receivedAt()) ? candidate : current);
		}
		Map<UUID, SubmissionScoreView> scores = this.gradingResults
			.findLatestScores(latestByAssignment.values().stream().map(SubmissionAssessmentView::submissionId).toList())
			.stream()
			.collect(Collectors.toMap(SubmissionScoreView::submissionId, Function.identity()));

		List<AssignmentResult> results = courseAssignments.stream()
			.sorted(Comparator.comparingInt(AssignmentView::displayOrder)
				.thenComparing(AssignmentView::title, String.CASE_INSENSITIVE_ORDER))
			.map((assignment) -> assignmentResult(assignment, latestByAssignment.get(assignment.id()), scores))
			.toList();
		return new CourseGroup(course.id(), course.name(),
				List.of(new ClassGroup(classId, className(course.id(), classId), results)));
	}

	private AssignmentResult assignmentResult(AssignmentView assignment, @Nullable SubmissionAssessmentView latest,
			Map<UUID, SubmissionScoreView> scores) {
		LatestAttempt attempt = (latest == null) ? null : latestAttempt(latest, scores.get(latest.submissionId()));
		return new AssignmentResult(assignment.id(), assignment.assignmentKey(), assignment.title(), attempt);
	}

	private static LatestAttempt latestAttempt(SubmissionAssessmentView submission,
			@Nullable SubmissionScoreView score) {
		boolean scored = score != null && score.status().isScored();
		return new LatestAttempt(submission.submissionId(), shortSha(submission.commitSha()), submission.receivedAt(),
				submission.late(), submission.status().name(), (score == null) ? null : score.status().name(),
				scored ? score.testsPassed() : null, scored ? score.testsTotal() : null,
				scored ? score.scorePercent() : null, scored ? score.passed() : null);
	}

	private @Nullable String className(UUID courseId, @Nullable UUID classId) {
		if (classId == null) {
			return null;
		}
		return this.courses.findClasses(courseId)
			.stream()
			.filter((courseClass) -> courseClass.id().equals(classId))
			.map(CourseClassView::name)
			.findFirst()
			.orElse(null);
	}

	private static Map<UUID, UUID> classByCourse(List<EnrollmentView> enrollments) {
		Map<UUID, UUID> classByCourse = new LinkedHashMap<>();
		for (EnrollmentView enrollment : enrollments) {
			// A null class id is a legitimate course-level enrollment, so it cannot go
			// through Map.merge, which rejects null values outright.
			if (classByCourse.get(enrollment.courseId()) == null) {
				classByCourse.put(enrollment.courseId(), enrollment.classId());
			}
		}
		return classByCourse;
	}

	private static String shortSha(String commitSha) {
		return (commitSha.length() <= SHORT_SHA_LENGTH) ? commitSha : commitSha.substring(0, SHORT_SHA_LENGTH);
	}

}
