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

package org.gitgrader.grading.internal;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.grading.StudentGradingResult;
import org.gitgrader.grading.StudentTestResultView;
import org.gitgrader.grading.TestOutcome;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.grading.domain.TestResultRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that a hidden check's name and output never reach the student.
 *
 * <p>
 * The whole hidden-suite model rests on this: a result link needs no account, so whatever
 * a student view carries is public. The guarantee spans two halves that are written far
 * apart - the record keeps the real name and the assertion output in one pair of columns
 * and what the student is told in another, and the read must take only the second pair -
 * so it is asserted here end to end, on the path that actually serves a result page.
 */
class HiddenTestSecrecyTest {

	private static final String SECRET_NAME = "test_rejects_sql_injection_via_union_select";

	private static final String SECRET_OUTPUT = "expected 0 rows, got 3 at /opt/hidden-tests/hidden.test.js:214";

	private final TestResultRepository results = mock(TestResultRepository.class);

	@Test
	@DisplayName("stores a hidden check's real name and output apart from what the student is shown")
	void storesTheSecretsInTheInstructorOnlyColumns() {
		TestResultRecord stored = record(hiddenResult());

		assertThat(stored.publicName()).isEqualTo("SQL injection");
		assertThat(stored.studentMessage()).isEqualTo("Consider how the query is built.");
		assertThat(stored.testName()).isEqualTo(SECRET_NAME);
		assertThat(stored.internalMessage()).isEqualTo(SECRET_OUTPUT);
	}

	@Test
	@DisplayName("serves a hidden check without its name or its assertion output")
	void neverServesTheSecretsToAStudent() {
		StudentTestResultView view = studentViewOf(record(hiddenResult()));

		assertThat(view.publicName()).isEqualTo("SQL injection");
		assertThat(view.toString()).doesNotContain(SECRET_NAME).doesNotContain(SECRET_OUTPUT);
	}

	@Test
	@DisplayName("gives the student the hint the instructor wrote for a hidden check")
	void servesTheHintForAHiddenCheck() {
		// The only thing a hidden failure can tell a student. The manifest carries one
		// per check and the record stores it, but it arrived in the message field while
		// the result page reads the hint field for a hidden check, so what an instructor
		// wrote to unblock a student was never shown to one.
		StudentTestResultView view = studentViewOf(record(hiddenResult()));

		assertThat(view.hint()).isEqualTo("Consider how the query is built.");
	}

	@Test
	@DisplayName("still names a public check and shows what it reported")
	void servesAPublicCheckInFull() {
		ParsedResult publicResult = new ParsedResult("PUBLIC", "Addition", "adds two numbers", null, TestOutcome.PASSED,
				BigDecimal.ONE, 12L, "You passed addition", "internal detail", null);

		StudentTestResultView view = studentViewOf(record(publicResult));

		assertThat(view.publicName()).isEqualTo("adds two numbers");
		assertThat(view.studentMessage()).isEqualTo("You passed addition");
	}

	private static ParsedResult hiddenResult() {
		return new ParsedResult(TestResultRecord.VISIBILITY_HIDDEN, "SQL injection", SECRET_NAME, null,
				TestOutcome.FAILED, BigDecimal.ONE, 150L, null, SECRET_OUTPUT, "Consider how the query is built.");
	}

	/**
	 * Runs the write half: what the executor persists for one parsed result.
	 * @param parsed the result the report parser produced
	 * @return the record as it reaches the database
	 */
	private TestResultRecord record(ParsedResult parsed) {
		GradingRun run = new GradingRun(UUID.randomUUID(), 1, "PUSH", null, null, null, "correlation",
				java.time.Clock.systemUTC());
		GradingExecutor executor = new GradingExecutor(mock(org.gitgrader.grading.GradingRunner.class),
				mock(GradingWorkspaceFactory.class), mock(GradingPlanResolver.class), mock(ReportParser.class),
				this.results, mock(org.gitgrader.configuration.GradingProperties.class),
				new tools.jackson.databind.ObjectMapper(), java.time.Clock.systemUTC());

		executor.persist(run,
				new GradingExecutor.Outcome(java.nio.file.Path.of("/tmp"), List.of(parsed),
						new org.gitgrader.grading.GradingScore(1, 1, 0, 0, 0, BigDecimal.TEN, BigDecimal.TEN, true), 0,
						5L, false, null));

		ArgumentCaptor<TestResultRecord> saved = ArgumentCaptor.forClass(TestResultRecord.class);
		verify(this.results).save(saved.capture());
		return saved.getValue();
	}

	/**
	 * Runs the read half: what a result page is given for one stored record.
	 * @param stored the record in the database
	 * @return the view handed to the student
	 */
	private StudentTestResultView studentViewOf(TestResultRecord stored) {
		GradingRunRepository runs = mock(GradingRunRepository.class);
		GradingRun run = new GradingRun(UUID.randomUUID(), 1, "PUSH", null, null, null, "correlation",
				java.time.Clock.systemUTC());
		run.complete(new org.gitgrader.grading.GradingScore(1, 1, 0, 0, 0, BigDecimal.TEN, BigDecimal.TEN, true), 0, 5L,
				java.time.Clock.systemUTC());
		when(runs.findFirstBySubmissionIdOrderByAttemptDesc(any())).thenReturn(Optional.of(run));
		when(this.results.findByGradingRunIdOrderByDisplayOrder(any())).thenReturn(List.of(stored));

		StudentGradingResult result = new DefaultGradingResultQuery(runs, this.results)
			.findLatestForSubmission(UUID.randomUUID())
			.orElseThrow();
		assertThat(result.status()).isNotNull();
		return result.tests().getFirst();
	}

}
