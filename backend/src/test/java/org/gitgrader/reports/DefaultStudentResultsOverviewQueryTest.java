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
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseClassView;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.courses.EnrollmentStatus;
import org.gitgrader.courses.EnrollmentView;
import org.gitgrader.grading.GradingResultQuery;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.SubmissionScoreView;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.identity.StudentView;
import org.gitgrader.reports.StudentResultsOverview.AssignmentResult;
import org.gitgrader.reports.StudentResultsOverview.CourseGroup;
import org.gitgrader.submissions.SubmissionAssessmentView;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultStudentResultsOverviewQuery}, which groups a student's attempts
 * by course and class for the per-student results link.
 *
 * <p>
 * The grouping is only trustworthy if a run that produced no score contributes no numbers
 * at all: "0 of 10 tests passed" is a claim about a grading run rather than about the
 * student, and a student can open this link before their first run has finished.
 */
class DefaultStudentResultsOverviewQueryTest {

	private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

	private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

	private static final UUID CLASS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

	private static final UUID ASSIGNMENT_ONE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

	private static final UUID ASSIGNMENT_TWO = UUID.fromString("00000000-0000-0000-0000-0000000000e2");

	private CourseCatalog courses;

	private AssignmentCatalog assignments;

	private StudentDirectory students;

	private SubmissionService submissions;

	private GradingResultQuery gradingResults;

	private DefaultStudentResultsOverviewQuery query;

	@BeforeEach
	void setUp() {
		this.courses = Mockito.mock(CourseCatalog.class);
		this.assignments = Mockito.mock(AssignmentCatalog.class);
		this.students = Mockito.mock(StudentDirectory.class);
		this.submissions = Mockito.mock(SubmissionService.class);
		this.gradingResults = Mockito.mock(GradingResultQuery.class);
		Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneId.of("UTC"));
		this.query = new DefaultStudentResultsOverviewQuery(this.courses, this.assignments, this.students,
				this.submissions, this.gradingResults, clock);

		when(this.students.findById(STUDENT)).thenReturn(Optional.of(student()));
		when(this.courses.findCourse(COURSE)).thenReturn(Optional.of(course()));
		when(this.courses.findClasses(COURSE))
			.thenReturn(List.of(new CourseClassView(CLASS_ID, COURSE, "class-a", "Class A")));
		when(this.courses.findEnrollments(STUDENT)).thenReturn(List.of(new EnrollmentView(UUID.randomUUID(), STUDENT,
				COURSE, CLASS_ID, EnrollmentStatus.ACTIVE, Instant.parse("2026-01-10T08:00:00Z"))));
		when(this.assignments.findByCourse(COURSE))
			.thenReturn(List.of(assignment(ASSIGNMENT_ONE, "assignment-01", "String utilities", 1),
					assignment(ASSIGNMENT_TWO, "assignment-02", "Collections", 2)));
		when(this.submissions.findAssessmentsForStudent(any(), any(), any())).thenReturn(List.of());
		when(this.gradingResults.findLatestScores(any())).thenReturn(List.of());
	}

	@Test
	@DisplayName("groups by course and class and keeps only the newest attempt per assignment")
	void groupsAndPicksNewestAttempt() {
		UUID older = UUID.randomUUID();
		UUID newer = UUID.randomUUID();
		when(this.submissions.findAssessmentsForStudent(eq(COURSE), eq(STUDENT), any())).thenReturn(List.of(
				assessment(older, ASSIGNMENT_ONE, "454d5a635fd9ce1eefa9abae955213a94af592ac",
						Instant.parse("2026-02-10T09:00:00Z"), false),
				assessment(newer, ASSIGNMENT_ONE, "912ff0aa11bb22cc33dd44ee55ff6600778899aa",
						Instant.parse("2026-02-20T09:00:00Z"), true)));
		when(this.gradingResults.findLatestScores(any()))
			.thenReturn(List.of(score(newer, GradingRunStatus.COMPLETED, 7, 10, new BigDecimal("70.0"), true),
					score(older, GradingRunStatus.COMPLETED, 1, 10, new BigDecimal("10.0"), false)));

		StudentResultsOverview overview = this.query.forStudent(STUDENT).orElseThrow();

		assertThat(overview.studentDisplayName()).isEqualTo("Max Muster");
		assertThat(overview.generatedAt()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
		assertThat(overview.courses()).hasSize(1);

		CourseGroup group = overview.courses().getFirst();
		assertThat(group.courseId()).isEqualTo(COURSE);
		assertThat(group.courseName()).isEqualTo("Example Programming");
		assertThat(group.classes()).hasSize(1);
		assertThat(group.classes().getFirst().classId()).isEqualTo(CLASS_ID);
		assertThat(group.classes().getFirst().className()).isEqualTo("Class A");

		List<AssignmentResult> results = group.classes().getFirst().assignments();
		assertThat(results).extracting(AssignmentResult::assignmentKey)
			.containsExactly("assignment-01", "assignment-02");

		StudentResultsOverview.LatestAttempt latest = results.getFirst().latest();
		assertThat(latest.submissionId()).isEqualTo(newer);
		assertThat(latest.shortCommitSha()).isEqualTo("912ff0a");
		assertThat(latest.late()).isTrue();
		assertThat(latest.submissionStatus()).isEqualTo(SubmissionStatus.PASSED.name());
		assertThat(latest.gradingStatus()).isEqualTo(GradingRunStatus.COMPLETED.name());
		assertThat(latest.testsPassed()).isEqualTo(7);
		assertThat(latest.testsTotal()).isEqualTo(10);
		assertThat(latest.scorePercent()).isEqualByComparingTo("70.0");
		assertThat(latest.passed()).isTrue();

		assertThat(results.get(1).latest()).isNull();
	}

	@Test
	@DisplayName("withholds counts and score from a run that is not scored")
	void withholdsUnscoredNumbers() {
		UUID submissionId = UUID.randomUUID();
		when(this.submissions.findAssessmentsForStudent(eq(COURSE), eq(STUDENT), any()))
			.thenReturn(List.of(assessment(submissionId, ASSIGNMENT_ONE, "912ff0aa11bb22cc33dd44ee55ff6600778899aa",
					Instant.parse("2026-02-20T09:00:00Z"), false)));
		when(this.gradingResults.findLatestScores(any()))
			.thenReturn(List.of(score(submissionId, GradingRunStatus.TIMEOUT, 4, 10, new BigDecimal("40.0"), false)));

		StudentResultsOverview.LatestAttempt latest = this.query.forStudent(STUDENT)
			.orElseThrow()
			.courses()
			.getFirst()
			.classes()
			.getFirst()
			.assignments()
			.getFirst()
			.latest();

		assertThat(latest.gradingStatus()).isEqualTo(GradingRunStatus.TIMEOUT.name());
		assertThat(latest.testsPassed()).isNull();
		assertThat(latest.testsTotal()).isNull();
		assertThat(latest.scorePercent()).isNull();
		assertThat(latest.passed()).isNull();
	}

	@Test
	@DisplayName("answers nothing for a student the directory does not know")
	void unknownStudentYieldsEmpty() {
		UUID stranger = UUID.randomUUID();

		assertThat(this.query.forStudent(stranger)).isEmpty();
	}

	@Test
	@DisplayName("files the assignments under an unnamed group when no class is set")
	void courseLevelEnrolmentHasNoClassGroup() {
		when(this.courses.findEnrollments(STUDENT)).thenReturn(List.of(new EnrollmentView(UUID.randomUUID(), STUDENT,
				COURSE, null, EnrollmentStatus.ACTIVE, Instant.parse("2026-01-10T08:00:00Z"))));

		StudentResultsOverview.ClassGroup group = this.query.forStudent(STUDENT)
			.orElseThrow()
			.courses()
			.getFirst()
			.classes()
			.getFirst();

		assertThat(group.classId()).isNull();
		assertThat(group.className()).isNull();
	}

	@Test
	@DisplayName("orders courses by name and assignments by display order")
	void ordersGroups() {
		UUID secondCourse = UUID.randomUUID();
		when(this.courses.findEnrollments(STUDENT)).thenReturn(List.of(
				new EnrollmentView(UUID.randomUUID(), STUDENT, COURSE, null, EnrollmentStatus.ACTIVE,
						Instant.parse("2026-01-10T08:00:00Z")),
				new EnrollmentView(UUID.randomUUID(), STUDENT, secondCourse, null, EnrollmentStatus.ACTIVE,
						Instant.parse("2026-01-10T08:00:00Z"))));
		when(this.courses.findCourse(secondCourse)).thenReturn(Optional.of(new CourseView(secondCourse, "algorithms",
				"Algorithms", null, null, null, null, "UTC", CourseStatus.ACTIVE, null, null, true)));
		when(this.assignments.findByCourse(secondCourse)).thenReturn(List.of());

		List<CourseGroup> groups = this.query.forStudent(STUDENT).orElseThrow().courses();

		assertThat(groups).extracting(CourseGroup::courseName).containsExactly("Algorithms", "Example Programming");
	}

	@Test
	@DisplayName("leaves a short commit hash untouched")
	void shortCommitHashIsNotPadded() {
		UUID submissionId = UUID.randomUUID();
		when(this.submissions.findAssessmentsForStudent(eq(COURSE), eq(STUDENT), any())).thenReturn(List
			.of(assessment(submissionId, ASSIGNMENT_ONE, "abc1234", Instant.parse("2026-02-20T09:00:00Z"), false)));

		StudentResultsOverview.LatestAttempt latest = this.query.forStudent(STUDENT)
			.orElseThrow()
			.courses()
			.getFirst()
			.classes()
			.getFirst()
			.assignments()
			.getFirst()
			.latest();

		assertThat(latest.shortCommitSha()).isEqualTo("abc1234");
	}

	private static StudentView student() {
		return new StudentView(STUDENT, "s1000042", "Max Muster", "max@example.org",
				StudentStatus.VERIFIED_BY_INSTRUCTOR, "CLASS-A", Instant.parse("2026-01-05T08:00:00Z"));
	}

	private static CourseView course() {
		return new CourseView(COURSE, "example-programming", "Example Programming", null, null, null, null, "UTC",
				CourseStatus.ACTIVE, null, null, true);
	}

	private static AssignmentView assignment(UUID id, String key, String title, int displayOrder) {
		return new AssignmentView(id, COURSE, key, title, null, displayOrder, AssignmentStatus.OPEN, true, null, null,
				"UTC", new BigDecimal("100"), 10, new BigDecimal("70"), false, null, null, null, null, null, null, null,
				false, false);
	}

	private static SubmissionAssessmentView assessment(UUID id, UUID assignmentId, String commitSha, Instant receivedAt,
			boolean late) {
		return new SubmissionAssessmentView(id, STUDENT, assignmentId,
				late ? SubmissionStatus.PASSED : SubmissionStatus.RECEIVED, commitSha, "refs/heads/main", null,
				receivedAt, late);
	}

	private static SubmissionScoreView score(UUID submissionId, GradingRunStatus status, int testsPassed,
			int testsTotal, BigDecimal scorePercent, Boolean passed) {
		return new SubmissionScoreView(submissionId, 1, status, testsPassed, testsTotal, scorePercent, null, passed,
				Instant.parse("2026-02-20T09:05:00Z"));
	}

}
