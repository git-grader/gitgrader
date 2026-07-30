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
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.GradingResultQuery;
import org.gitgrader.grading.StudentGradingResult;
import org.gitgrader.grading.StudentTestResultView;
import org.gitgrader.grading.TestOutcome;
import org.gitgrader.security.RateLimiter;
import org.gitgrader.security.ResultTokenService;
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

	private ResultTokenService tokens;

	private GradingResultQuery gradingResults;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.tokens = mock(ResultTokenService.class);
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
			.standaloneSetup(new ResultController(this.tokens, submissions, assignments, courses, this.gradingResults,
					rateLimiter))
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
		// before anything has been graded. That is a page with no results yet, not a 500.
		this.mockMvc.perform(get("/api/v1/results/good-token"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.total").value(0))
			.andExpect(jsonPath("$.tests.length()").value(0));
	}

	private static StudentGradingResult result() {
		return new StudentGradingResult(GradingRunStatus.COMPLETED, 1, 2, new BigDecimal("50.0"), false,
				List.of(new StudentTestResultView("PUBLIC", null, "truncate keeps short text", TestOutcome.PASSED, 3L,
						null, null),
						new StudentTestResultView("HIDDEN", "Slug generation", "Slug generation", TestOutcome.FAILED,
								4L, "Normalise accented letters.", null)));
	}

	private static SubmissionView submission() {
		return new SubmissionView(SUBMISSION, UUID.randomUUID(), "course/assignment/s1", UUID.randomUUID(), COURSE,
				ASSIGNMENT, "454d5a635fd9ce1eefa9abae955213a94af592ac", "454d5a6", "refs/heads/main", null,
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

}
