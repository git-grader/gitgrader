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

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.servlet.http.HttpServletRequest;
import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.grading.GradingResultQuery;
import org.gitgrader.grading.StudentGradingResult;
import org.gitgrader.grading.StudentTestResultView;
import org.gitgrader.security.ClientAddress;
import org.gitgrader.security.RateLimiter;
import org.gitgrader.security.ResultTokenService;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionView;
import org.jspecify.annotations.Nullable;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Serves the result page a student reaches from the link printed by their push.
 *
 * <p>
 * The token is the only credential, so anything that is not a straightforward answer is
 * reported the same way. An unknown token, an expired one, and a token for a submission
 * that has since been removed all produce the same 404: distinguishing them would turn
 * this into an oracle for guessing tokens.
 */
@RestController
@RequestMapping("/api/v1/results")
public class ResultController {

	private final ResultTokenService resultTokens;

	private final SubmissionService submissions;

	private final AssignmentCatalog assignments;

	private final CourseCatalog courses;

	private final GradingResultQuery gradingResults;

	private final RateLimiter rateLimiter;

	public ResultController(ResultTokenService resultTokens, SubmissionService submissions,
			AssignmentCatalog assignments, CourseCatalog courses, GradingResultQuery gradingResults,
			RateLimiter rateLimiter) {
		this.resultTokens = resultTokens;
		this.submissions = submissions;
		this.assignments = assignments;
		this.courses = courses;
		this.gradingResults = gradingResults;
		this.rateLimiter = rateLimiter;
	}

	@GetMapping("/{token}")
	public ResponseEntity<PublicResultView> result(@PathVariable String token, HttpServletRequest request) {
		if (!this.rateLimiter.tryConsumeResultLookupPerIp(ClientAddress.of(request))) {
			throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many result lookups.");
		}
		SubmissionView submission = this.resultTokens.resolve(token)
			.flatMap(this.submissions::findById)
			.orElseThrow(ResultController::notFound);
		AssignmentView assignment = this.assignments.findAssignment(submission.assignmentId())
			.orElseThrow(ResultController::notFound);
		String courseName = this.courses.findCourse(submission.courseId())
			.orElseThrow(ResultController::notFound)
			.name();

		Optional<StudentGradingResult> graded = this.gradingResults.findLatestForSubmission(submission.id());
		// Absent rather than zero when there is no score. A run that timed out or broke
		// leaves it null precisely so that it cannot be read as a mark, and answering
		// zero here rebuilt on the student's own result page the confusion the domain
		// refuses to write to the database. The counts go the same way: "0 of 0 tests
		// passed" is a sentence about a run that happened, and this one did not.
		PublicResultView result = new PublicResultView(assignment.title(), courseName, submission.commitSha(),
				submission.receivedAt(), submission.signatureVerified(),
				graded.map(StudentGradingResult::testsPassed).orElse(null),
				graded.map(StudentGradingResult::testsTotal).orElse(null),
				graded.map(StudentGradingResult::scorePercent).orElse(null),
				graded.map(StudentGradingResult::tests).orElse(List.of()).stream().map(PublicTestView::of).toList());
		// The link in the URL is the whole credential, so this response must not be kept
		// by a shared cache or written to disk by the browser.
		return ResponseEntity.ok().cacheControl(CacheControl.noStore().cachePrivate()).body(result);
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "No result for this link.");
	}

	/**
	 * The result as a student sees it.
	 *
	 * @param assignmentTitle the assignment that was submitted
	 * @param courseName the course it belongs to
	 * @param commitSha the graded commit
	 * @param receivedAt when the push was accepted
	 * @param verified whether the commit signature was accepted
	 * @param passed how many checks passed, absent when the run produced no result
	 * @param total how many checks the suite declares, absent when the run produced no
	 * result
	 * @param score the percentage recorded for the run
	 * @param tests the per-check outcomes
	 */
	public record PublicResultView(String assignmentTitle, String courseName, String commitSha, Instant receivedAt,
			boolean verified, @Nullable Integer passed, @Nullable Integer total, @Nullable BigDecimal score,
			List<PublicTestView> tests) {
	}

	/**
	 * One check, as a student sees it.
	 *
	 * @param isPublic whether the check is one the student can read in their own
	 * repository
	 * @param name the check's public name, absent for a hidden check
	 * @param category the grouping a hidden check belongs to
	 * @param outcome how the check ended
	 * @param message the message the student is allowed to see
	 * @param hint guidance attached to the check
	 */
	public record PublicTestView(@JsonProperty("public") boolean isPublic, @Nullable String name,
			@Nullable String category, String outcome, @Nullable String message, @Nullable String hint) {

		static PublicTestView of(StudentTestResultView view) {
			return new PublicTestView(!"HIDDEN".equals(view.visibility()), view.publicName(), view.category(),
					view.outcome().name(), view.studentMessage(), view.hint());
		}
	}

}
