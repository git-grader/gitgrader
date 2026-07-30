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

package org.gitgrader.grading.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.gitgrader.grading.FailureCategory;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.GradingScore;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link GradingRun} lifecycle.
 *
 * <p>
 * The distinction this class exists to protect is between <em>the student's code
 * failed</em> and <em>grading itself failed</em>. Conflating the two is the most damaging
 * bug this system could have: a broken container or a crashed runner must never be
 * recorded as a legitimate zero, because that silently assigns a grade nobody earned.
 */
class GradingRunTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T10:15:30Z"), ZoneOffset.UTC);

	@Test
	@DisplayName("a new run starts queued and unscored")
	void startsQueued() {
		GradingRun run = newRun();

		assertThat(run.status()).isEqualTo(GradingRunStatus.QUEUED);
		assertThat(run.id()).isNotNull();
		assertThat(run.attempt()).isEqualTo(1);
		// Nothing has been graded yet, so there must be no score to misread as a real
		// one.
		assertThat(run.scorePercent()).isNull();
		assertThat(run.pointsAwarded()).isNull();
		assertThat(run.passed()).isNull();
		assertThat(run.failureCategory()).isNull();
	}

	@Test
	@DisplayName("claiming the run moves it to running")
	void marksRunning() {
		GradingRun run = newRun();

		run.markRunning(CLOCK);

		assertThat(run.status()).isEqualTo(GradingRunStatus.RUNNING);
	}

	@Test
	@DisplayName("a fully passing run records the score and no failure category")
	void completesWhenAllTestsPass() {
		GradingRun run = newRun();
		run.markRunning(CLOCK);

		run.complete(score(10, 10, 0, new BigDecimal("100.0"), new BigDecimal("10.0"), true), 0, 4200L, CLOCK);

		assertThat(run.status()).isEqualTo(GradingRunStatus.COMPLETED);
		assertThat(run.testsPassed()).isEqualTo(10);
		assertThat(run.testsTotal()).isEqualTo(10);
		assertThat(run.scorePercent()).isEqualByComparingTo("100.0");
		assertThat(run.pointsAwarded()).isEqualByComparingTo("10.0");
		assertThat(run.passed()).isTrue();
		assertThat(run.failureCategory()).isNull();
	}

	@Test
	@DisplayName("failing tests still complete the run, attributed to the submission")
	void failingTestsAreNotAnInfrastructureFailure() {
		GradingRun run = newRun();
		run.markRunning(CLOCK);

		run.complete(score(10, 7, 3, new BigDecimal("70.0"), new BigDecimal("7.0"), false), 1, 5100L, CLOCK);

		// The grading pipeline did its job, so the run is COMPLETED rather than FAILED.
		// A partial result is a real, reportable outcome - the student scored 70%.
		assertThat(run.status()).isEqualTo(GradingRunStatus.COMPLETED);
		assertThat(run.scorePercent()).isEqualByComparingTo("70.0");
		assertThat(run.passed()).isFalse();
		// Attribution matters: this is the student's test failure, not a runner defect.
		assertThat(run.failureCategory()).isEqualTo(FailureCategory.STUDENT_TEST_FAILURE);
	}

	@Test
	@DisplayName("an infrastructure failure records no score at all")
	void infrastructureFailureLeavesNoScore() {
		GradingRun run = newRun();
		run.markRunning(CLOCK);

		run.fail(FailureCategory.INFRASTRUCTURE_ERROR, "container runtime unavailable",
				GradingRunStatus.INFRASTRUCTURE_ERROR, CLOCK);

		assertThat(run.status()).isEqualTo(GradingRunStatus.INFRASTRUCTURE_ERROR);
		assertThat(run.failureCategory()).isEqualTo(FailureCategory.INFRASTRUCTURE_ERROR);
		// The crucial assertion: a broken runner must not leave behind a zero that
		// downstream reporting would present as the student's earned grade.
		assertThat(run.scorePercent()).isNull();
		assertThat(run.pointsAwarded()).isNull();
		assertThat(run.passed()).isNull();
	}

	@Test
	@DisplayName("a timeout is recorded as a timeout, not as a wrong answer")
	void timeoutIsDistinguishable() {
		GradingRun run = newRun();
		run.markRunning(CLOCK);

		run.fail(FailureCategory.RUNNER_ERROR, "exceeded wall clock limit", GradingRunStatus.TIMEOUT, CLOCK);

		assertThat(run.status()).isEqualTo(GradingRunStatus.TIMEOUT);
		assertThat(run.scorePercent()).isNull();
	}

	@Test
	@DisplayName("each run carries its own identity and correlation id")
	void runsAreIndividuallyIdentifiable() {
		GradingRun first = newRun();
		GradingRun second = newRun();

		// Retries of the same submission must be separable in the audit trail.
		assertThat(first.id()).isNotEqualTo(second.id());
		assertThat(first.correlationId()).isEqualTo("corr-1");
		assertThat(first.submissionId()).isEqualTo(second.submissionId());
	}

	private static GradingRun newRun() {
		return new GradingRun(UUID.fromString("00000000-0000-0000-0000-0000000000aa"), 1, "PUSH", null, null, null,
				"corr-1", CLOCK);
	}

	private static GradingScore score(int total, int passed, int failed, BigDecimal percent, BigDecimal points,
			boolean successful) {
		return new GradingScore(total, passed, failed, 0, 0, percent, points, successful);
	}

}
