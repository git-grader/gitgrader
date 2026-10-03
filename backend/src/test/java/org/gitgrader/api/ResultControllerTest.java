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
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.AppProperties;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.GradingResultQuery;
import org.gitgrader.grading.StudentGradingResult;
import org.gitgrader.grading.StudentTestResultView;
import org.gitgrader.grading.TestOutcome;
import org.gitgrader.reports.StudentResultsOverview;
import org.gitgrader.reports.StudentResultsOverviewQuery;
import org.gitgrader.security.RateLimiter;
import org.gitgrader.security.ResultTokenService;
import org.gitgrader.security.StudentResultsOverviewTokenService;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link ResultController}.
 *
 * <p>
 * This endpoint is reachable by anyone holding the link, so the assertions are mostly
 * about what must <em>not</em> come back: the name of a hidden check, and any way to tell
 * a token that never existed from one that did.
 */
class ResultControllerTest {

	private static final UUID SUBMISSION = UUID.fromString("00000000-0000-0000-0000-0000000000b1");

	private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

	private static final UUID ASSIGNMENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

	private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

	private static final UUID CLASS_ID = UUID.fromString("00000000-0000-0000-0000-0000000000d1");

	private ResultTokenService tokens;

	private StudentResultsOverviewTokenService overviewTokens;

	private StudentResultsOverviewQuery overviewQuery;

	private GradingResultQuery gradingResults;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.tokens = mock(ResultTokenService.class);
		this.overviewTokens = mock(StudentResultsOverviewTokenService.class);
		this.overviewQuery = mock(StudentResultsOverviewQuery.class);
		this.gradingResults = mock(GradingResultQuery.class);
		SubmissionService submissions = mock(SubmissionService.class);
		AssignmentCatalog assignments = mock(AssignmentCatalog.class);
		CourseCatalog courses = mock(CourseCatalog.class);
		RateLimiter rateLimiter = mock(RateLimiter.class);
		when(rateLimiter.tryConsumeResultLookupPerIp(any())).thenReturn(true);
		when(submissions.findById(SUBMISSION)).thenReturn(Optional.of(submission()));
		when(assignments.findAssignment(ASSIGNMENT)).thenReturn(Optional.of(assignment()));
		when(courses.findCourse(COURSE)).thenReturn(Optional.of(course()));
		this.mockMvc = MockMvcBuilders
			.standaloneSetup(new ResultController(this.tokens, this.overviewTokens, this.overviewQuery, submissions,
					assignments, courses, this.gradingResults, rateLimiter, appProperties()))
			.build();
	}

	@Test
	@DisplayName("reports the score and every check that ran")
	void reportsTheScore() throws Exception {
		when(this.tokens.resolve("good-token")).thenReturn(Optional.of(SUBMISSION));
		when(this.gradingResults.findLatestForSubmission(SUBMISSION)).thenReturn(Optional.of(result()));

		this.mockMvc.perform(get("/api/v1/results/good-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignmentTitle").value("String utilities"))
			.andExpect(jsonPath("$.courseName").value("Example Programming"))
			.andExpect(jsonPath("$.verified").value(true))
			.andExpect(jsonPath("$.passed").value(1))
			.andExpect(jsonPath("$.total").value(2))
			.andExpect(jsonPath("$.score").value(50.0))
			.andExpect(jsonPath("$.tests.length()").value(2));
	}

	@Test
	@DisplayName("never names a hidden check")
	void keepsHiddenChecksHidden() throws Exception {
		when(this.tokens.resolve("good-token")).thenReturn(Optional.of(SUBMISSION));
		when(this.gradingResults.findLatestForSubmission(SUBMISSION)).thenReturn(Optional.of(result()));

		// The hidden check's real name is what the whole redaction exists to withhold:
		// leaking it hands the suite to anyone who was ever sent a result link.
		this.mockMvc.perform(get("/api/v1/results/good-token"))
			.andExpect(content().string(
					org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("h07 slugify strips diacritics"))))
			.andExpect(jsonPath("$.tests[1].public").value(false))
			.andExpect(jsonPath("$.tests[1].category").value("Slug generation"))
			.andExpect(jsonPath("$.tests[0].public").value(true));
	}

	@Test
	@DisplayName("answers an unusable link the same way whatever is wrong with it")
	void unknownTokenIsIndistinguishable() throws Exception {
		when(this.tokens.resolve("nonsense")).thenReturn(Optional.empty());

		this.mockMvc.perform(get("/api/v1/results/nonsense")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("answers before the first run has finished")
	void toleratesAnUngradedSubmission() throws Exception {
		when(this.tokens.resolve("good-token")).thenReturn(Optional.of(SUBMISSION));
		when(this.gradingResults.findLatestForSubmission(SUBMISSION)).thenReturn(Optional.empty());

		// A student can open the link the moment the push prints it, which is normally
		// before anything has been graded. That is a page with no results yet, not a 500
		// -
		// and not "0 of 0 tests passed" either, which is a sentence about a run that
		// happened.
		this.mockMvc.perform(get("/api/v1/results/good-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.passed").doesNotExist())
			.andExpect(jsonPath("$.total").doesNotExist())
			.andExpect(jsonPath("$.score").doesNotExist())
			.andExpect(jsonPath("$.tests.length()").value(0))
			.andExpect(header().string("Cache-Control", containsString("no-store")));
	}

	@Test
	@DisplayName("lists the last attempt per assignment with a scoped report link")
	void overviewListsAttempts() throws Exception {
		when(this.overviewTokens.resolve("ov-token")).thenReturn(Optional.of(STUDENT));
		when(this.overviewQuery.forStudent(STUDENT)).thenReturn(Optional.of(overview()));

		this.mockMvc.perform(get("/api/v1/results/overview/ov-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentDisplayName").value("Max Muster"))
			.andExpect(jsonPath("$.courses[0].courseName").value("Example Programming"))
			.andExpect(jsonPath("$.courses[0].classes[0].className").value("Class A"))
			.andExpect(jsonPath("$.courses[0].classes[0].assignments[0].assignmentKey").value("assignment-01"))
			.andExpect(
					jsonPath("$.courses[0].classes[0].assignments[0].latest.submissionId").value(SUBMISSION.toString()))
			.andExpect(jsonPath("$.courses[0].classes[0].assignments[0].latest.shortCommitSha").value("454d5a6"))
			.andExpect(jsonPath("$.courses[0].classes[0].assignments[0].latest.resultUrl")
				.value(containsString("/results/overview/ov-token/submissions/" + SUBMISSION)))
			.andExpect(jsonPath("$.courses[0].classes[0].assignments[1].latest").doesNotExist())
			.andExpect(header().string("Cache-Control", containsString("no-store")));
	}

	@Test
	@DisplayName("answers an unusable overview link the same way whatever is wrong with it")
	void overviewUnknownTokenIsNotFound() throws Exception {
		when(this.overviewTokens.resolve("nonsense")).thenReturn(Optional.empty());

		this.mockMvc.perform(get("/api/v1/results/overview/nonsense")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("404s when the token no longer maps to a reportable student")
	void overviewMissingStudentIsNotFound() throws Exception {
		when(this.overviewTokens.resolve("ov-token")).thenReturn(Optional.of(STUDENT));
		when(this.overviewQuery.forStudent(STUDENT)).thenReturn(Optional.empty());

		this.mockMvc.perform(get("/api/v1/results/overview/ov-token")).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("opens the owner's submission through the overview link, still redacted")
	void scopedSubmissionOpensOwnSubmission() throws Exception {
		when(this.overviewTokens.resolve("ov-token")).thenReturn(Optional.of(STUDENT));
		when(this.gradingResults.findLatestForSubmission(SUBMISSION)).thenReturn(Optional.of(result()));

		this.mockMvc.perform(get("/api/v1/results/overview/ov-token/submissions/" + SUBMISSION))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.assignmentTitle").value("String utilities"))
			.andExpect(jsonPath("$.tests[1].public").value(false))
			.andExpect(content().string(not(containsString("h07 slugify strips diacritics"))));
	}

	@Test
	@DisplayName("refuses to open another student's submission through an overview link")
	void scopedSubmissionForOtherStudentIsNotFound() throws Exception {
		when(this.overviewTokens.resolve("ov-token")).thenReturn(Optional.of(UUID.randomUUID()));

		this.mockMvc.perform(get("/api/v1/results/overview/ov-token/submissions/" + SUBMISSION))
			.andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("refuses a scoped submission link carrying an unknown token")
	void scopedSubmissionUnknownTokenIsNotFound() throws Exception {
		when(this.overviewTokens.resolve("nonsense")).thenReturn(Optional.empty());

		this.mockMvc.perform(get("/api/v1/results/overview/nonsense/submissions/" + SUBMISSION))
			.andExpect(status().isNotFound());
	}

	private static StudentResultsOverview overview() {
		StudentResultsOverview.LatestAttempt latest = new StudentResultsOverview.LatestAttempt(SUBMISSION, "454d5a6",
				Instant.parse("2026-07-30T12:00:00Z"), false, SubmissionStatus.PASSED.name(),
				GradingRunStatus.COMPLETED.name(), 1, 2, new BigDecimal("50.0"), true);
		StudentResultsOverview.AssignmentResult withAttempt = new StudentResultsOverview.AssignmentResult(ASSIGNMENT,
				"assignment-01", "String utilities", latest);
		StudentResultsOverview.AssignmentResult withoutAttempt = new StudentResultsOverview.AssignmentResult(
				UUID.randomUUID(), "assignment-02", "Collections", null);
		StudentResultsOverview.ClassGroup classGroup = new StudentResultsOverview.ClassGroup(CLASS_ID, "Class A",
				List.of(withAttempt, withoutAttempt));
		StudentResultsOverview.CourseGroup courseGroup = new StudentResultsOverview.CourseGroup(COURSE,
				"Example Programming", List.of(classGroup));
		return new StudentResultsOverview("Max Muster", Instant.parse("2026-07-30T12:00:00Z"), List.of(courseGroup));
	}

	private static StudentGradingResult result() {
		return new StudentGradingResult(GradingRunStatus.COMPLETED, 1, 2, new BigDecimal("50.0"), false,
				List.of(new StudentTestResultView("PUBLIC", null, "truncate keeps short text", TestOutcome.PASSED, 3L,
						null, null),
						new StudentTestResultView("HIDDEN", "Slug generation", "Slug generation", TestOutcome.FAILED,
								4L, "Normalise accented letters.", null)));
	}

	private static SubmissionView submission() {
		return new SubmissionView(SUBMISSION, UUID.randomUUID(), "course/assignment/s1", STUDENT, COURSE, ASSIGNMENT,
				"454d5a635fd9ce1eefa9abae955213a94af592ac", "454d5a6", "refs/heads/main", null,
				Instant.parse("2026-07-30T12:00:00Z"), SignatureVerdict.VERIFIED, "SHA256:abc", SubmissionStatus.FAILED,
				false, null, null, null);
	}

	private static AssignmentView assignment() {
		return new AssignmentView(ASSIGNMENT, COURSE, "assignment-01", "String utilities", null, 1,
				AssignmentStatus.OPEN, true, null, null, "UTC", new BigDecimal("100"), 10, new BigDecimal("70"), false,
				null, null, null, null, null, null, null, false);
	}

	private static CourseView course() {
		return new CourseView(COURSE, "example-programming", "Example Programming", null, null, null, null, "UTC",
				CourseStatus.ACTIVE, null, null, true);
	}

	private static AppProperties appProperties() {
		return new AppProperties("GitGrader", URI.create("https://localhost"), "support@example.org",
				"Example Organization", URI.create("https://docs.example.org"), ZoneId.of("UTC"), "/data",
				new AppProperties.Registration(true, false, 5),
				new AppProperties.ResultTokens(256, Duration.ofDays(180), 8));
	}

}
