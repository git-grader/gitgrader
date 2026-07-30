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
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.gitgrader.reports.ReportCalculator.Assessment;
import org.gitgrader.reports.ReportCalculator.Assignment;
import org.gitgrader.reports.ReportCalculator.Student;
import org.gitgrader.submissions.SubmissionStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReportCalculatorTest {

	private static final UUID STUDENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final UUID FIRST_ASSIGNMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");

	private static final UUID SECOND_ASSIGNMENT_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-30T12:00:00Z"), ZoneOffset.UTC);

	@Test
	void calculatesCompletionRateSeparatelyFromPointsRate() {
		Student student = new Student(STUDENT_ID, "s1", "Ada Lovelace");
		List<Assignment> assignments = List.of(
				new Assignment(FIRST_ASSIGNMENT_ID, "small", true, new BigDecimal("10"), new BigDecimal("50")),
				new Assignment(SECOND_ASSIGNMENT_ID, "large", true, new BigDecimal("90"), new BigDecimal("50")));
		List<Assessment> assessments = List.of(
				new Assessment(FIRST_ASSIGNMENT_ID, SubmissionStatus.PASSED, new BigDecimal("100"), CLOCK.instant()),
				new Assessment(SECOND_ASSIGNMENT_ID, SubmissionStatus.FAILED, BigDecimal.ZERO, CLOCK.instant()));

		StudentProgressRow result = ReportCalculator.calculate(student, assignments, assessments);

		assertThat(result.completionRate()).isEqualByComparingTo("0.500000");
		assertThat(result.pointsRate()).isEqualByComparingTo("0.100000");
	}

	@Test
	void excludesInfrastructureErrorsFromAttemptsAndActivity() {
		Student student = new Student(STUDENT_ID, "s1", "Ada Lovelace");
		List<Assignment> assignments = List
			.of(new Assignment(FIRST_ASSIGNMENT_ID, "one", true, new BigDecimal("10"), new BigDecimal("50")));
		List<Assessment> assessments = List
			.of(new Assessment(FIRST_ASSIGNMENT_ID, SubmissionStatus.INFRASTRUCTURE_ERROR, null, CLOCK.instant()));

		StudentProgressRow result = ReportCalculator.calculate(student, assignments, assessments);

		assertThat(result.notStarted()).isEqualTo(1);
		assertThat(result.submissionCount()).isZero();
		assertThat(result.lastActivityAt()).isNull();
	}

	@Test
	void totalPointsCarriesThePointsAvailableNotThePointsEarned() {
		// Regression guard. An earlier revision passed pointsEarned into both fields, so
		// the two columns were silently identical and the points-rate denominator never
		// reached the export.
		Student student = new Student(STUDENT_ID, "s1", "Ada Lovelace");
		List<Assignment> assignments = List.of(
				new Assignment(FIRST_ASSIGNMENT_ID, "one", true, new BigDecimal("100"), new BigDecimal("50")),
				new Assignment(SECOND_ASSIGNMENT_ID, "two", true, new BigDecimal("100"), new BigDecimal("50")));
		List<Assessment> assessments = List
			.of(new Assessment(FIRST_ASSIGNMENT_ID, SubmissionStatus.PASSED, new BigDecimal("50"), CLOCK.instant()));

		StudentProgressRow result = ReportCalculator.calculate(student, assignments, assessments);

		assertThat(result.pointsEarned()).isEqualByComparingTo("50");
		assertThat(result.totalPoints()).isEqualByComparingTo("200");
		assertThat(result.totalPoints()).isNotEqualByComparingTo(result.pointsEarned());
	}

	@Test
	void takesTheBestAttemptRatherThanTheMostRecent() {
		// A student who reaches the threshold and then experiments must not be punished
		// for whatever their last push happened to score.
		Student student = new Student(STUDENT_ID, "s1", "Ada Lovelace");
		List<Assignment> assignments = List
			.of(new Assignment(FIRST_ASSIGNMENT_ID, "one", true, new BigDecimal("100"), new BigDecimal("50")));
		List<Assessment> assessments = List.of(
				new Assessment(FIRST_ASSIGNMENT_ID, SubmissionStatus.PASSED, new BigDecimal("100"), CLOCK.instant()),
				new Assessment(FIRST_ASSIGNMENT_ID, SubmissionStatus.FAILED, new BigDecimal("10"), CLOCK.instant()));

		StudentProgressRow result = ReportCalculator.calculate(student, assignments, assessments);

		assertThat(result.fullyCompleted()).isEqualTo(1);
		assertThat(result.pointsEarned()).isEqualByComparingTo("100");
	}

}
