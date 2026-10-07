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

package org.gitgrader.grading;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the score arithmetic of {@link GradingScorer}.
 *
 * <p>
 * Percentages are rounded to one decimal place and awarded points to two, so a student
 * can neither gain nor lose a mark to the order the arithmetic happens in. Points are
 * derived from the exact ratio and rounded only afterwards; rounding first and scaling
 * afterwards would hand a submission of a few hundredths of a point either the full mark
 * or none of it.
 *
 * <p>
 * The second half of the class pins the contract that makes per-test weights safe to
 * introduce. A manifest that declares nothing must score identically to the count-based
 * formula, skipped tests keep their weight in the denominator, and an unusable weight is
 * read as one, so no manifest can manufacture marks. An infrastructure error yields no
 * score at all, because a broken harness must never be recorded as a student's failure.
 */
class GradingScorerTest {

	@Test
	@DisplayName("weights each test by what its manifest declared")
	void weightsEachTest() {
		// One test worth five, one worth one, failing the expensive one. Counting tests
		// would
		// call this fifty percent; the manifest says it is worth one sixth.
		List<WeightedOutcome> outcomes = List.of(new WeightedOutcome(TestOutcome.PASSED, BigDecimal.valueOf(5)),
				new WeightedOutcome(TestOutcome.FAILED, BigDecimal.ONE));

		GradingScore score = GradingScorer.weighted(outcomes, BigDecimal.valueOf(60), BigDecimal.valueOf(50));

		assertThat(score).isNotNull();
		assertThat(score.scorePercent().toPlainString()).isEqualTo("83.3");
		assertThat(score.pointsAwarded().toPlainString()).isEqualTo("50.00");
		assertThat(score.passed()).isTrue();
		// The counts still report tests, not weights: the UI shows how many checks ran.
		assertThat(score.testsTotal()).isEqualTo(2);
		assertThat(score.testsPassed()).isEqualTo(1);
	}

	@Test
	@DisplayName("scores identically to the count-based formula when no test declares a weight")
	void weightsOfOneMatchTheCountBasedFormula() {
		// This is the property that makes enabling weights on new assignments safe. A
		// manifest
		// that says nothing about weights must not be able to move anyone's score.
		List<TestOutcome> mixed = List.of(TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED,
				TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.FAILED,
				TestOutcome.FAILED, TestOutcome.FAILED, TestOutcome.NOT_EXECUTED);

		GradingScore counted = GradingScorer.score(mixed, new BigDecimal("37.5"), new BigDecimal("42.25"));
		GradingScore weighted = GradingScorer.weighted(
				mixed.stream().map((outcome) -> new WeightedOutcome(outcome, BigDecimal.ONE)).toList(),
				new BigDecimal("37.5"), new BigDecimal("42.25"));

		assertThat(counted).isEqualTo(weighted);
	}

	@Test
	@DisplayName("counts a skipped test's weight in the denominator, as the count-based formula counted the test")
	void skippedWeightsStillCountTowardsTheTotal() {
		// Weight changes what a test is worth, not which tests count. Dropping skipped
		// tests
		// from the total would quietly turn a skipped expensive check into no penalty at
		// all.
		List<WeightedOutcome> outcomes = List.of(new WeightedOutcome(TestOutcome.PASSED, BigDecimal.ONE),
				new WeightedOutcome(TestOutcome.NOT_EXECUTED, BigDecimal.valueOf(9)));

		GradingScore score = GradingScorer.weighted(outcomes, BigDecimal.valueOf(10), BigDecimal.valueOf(50));

		assertThat(score).isNotNull();
		assertThat(score.scorePercent().toPlainString()).isEqualTo("10.0");
		assertThat(score.passed()).isFalse();
	}

	@Test
	@DisplayName("treats a missing, zero or negative weight as an ordinary test")
	void normalisesUnusableWeights() {
		List<WeightedOutcome> outcomes = List.of(new WeightedOutcome(TestOutcome.PASSED, null),
				new WeightedOutcome(TestOutcome.FAILED, BigDecimal.valueOf(-3)),
				new WeightedOutcome(TestOutcome.FAILED, BigDecimal.ZERO));

		GradingScore score = GradingScorer.weighted(outcomes, BigDecimal.valueOf(30), BigDecimal.valueOf(50));

		// All three count as one, which is exactly what the count-based formula would
		// have done.
		// A negative weight must never be able to award marks for failing.
		assertThat(score).isNotNull();
		assertThat(score.scorePercent().toPlainString()).isEqualTo("33.3");
		assertThat(score.pointsAwarded().toPlainString()).isEqualTo("10.00");
	}

	@Test
	@DisplayName("refuses to score a weighted run that hit an infrastructure error")
	void weightedRunRefusesInfrastructureError() {
		List<WeightedOutcome> outcomes = List.of(new WeightedOutcome(TestOutcome.PASSED, BigDecimal.valueOf(9)),
				new WeightedOutcome(TestOutcome.INFRASTRUCTURE_ERROR, BigDecimal.ONE));

		assertThat(GradingScorer.weighted(outcomes, BigDecimal.valueOf(100), BigDecimal.valueOf(50))).isNull();
	}

	@Test
	@DisplayName("scores seven of ten as exactly 70.0 %")
	void scoresSevenOfTenAsExactlySeventy() {
		List<TestOutcome> outcomes = List.of(TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED,
				TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.FAILED,
				TestOutcome.FAILED, TestOutcome.FAILED);

		GradingScore score = GradingScorer.score(outcomes, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

		assertThat(score).isNotNull();
		assertThat(score.scorePercent().toPlainString()).isEqualTo("70.0");
	}

	@Test
	@DisplayName("scores zero of ten")
	void scoresZeroOfTen() {
		List<TestOutcome> outcomes = Collections.nCopies(10, TestOutcome.FAILED);

		GradingScore score = GradingScorer.score(outcomes, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

		assertThat(score).isNotNull();
		assertThat(score.scorePercent().toPlainString()).isEqualTo("0.0");
	}

	@Test
	@DisplayName("scores ten of ten")
	void scoresTenOfTen() {
		List<TestOutcome> outcomes = Collections.nCopies(10, TestOutcome.PASSED);

		GradingScore score = GradingScorer.score(outcomes, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

		assertThat(score).isNotNull();
		assertThat(score.scorePercent().toPlainString()).isEqualTo("100.0");
	}

	@Test
	@DisplayName("scores one of three")
	void scoresOneOfThree() {
		List<TestOutcome> outcomes = List.of(TestOutcome.PASSED, TestOutcome.FAILED, TestOutcome.FAILED);

		GradingScore score = GradingScorer.score(outcomes, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

		assertThat(score).isNotNull();
		assertThat(score.scorePercent().toPlainString()).isEqualTo("33.3");
	}

	@Test
	@DisplayName("scores two of three")
	void scoresTwoOfThree() {
		List<TestOutcome> outcomes = List.of(TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.FAILED);

		GradingScore score = GradingScorer.score(outcomes, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

		assertThat(score).isNotNull();
		assertThat(score.scorePercent().toPlainString()).isEqualTo("66.7");
		assertThat(score.pointsAwarded()).isEqualByComparingTo("66.67");
	}

	@Test
	@DisplayName("rounds the awarded points only after applying the exact ratio")
	void roundsPointsOnlyAfterApplyingTheExactRatio() {
		List<TestOutcome> outcomes = List.of(TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.FAILED);

		GradingScore score = GradingScorer.score(outcomes, new BigDecimal("0.07"), new BigDecimal("66.7"));

		assertThat(score).isNotNull();
		assertThat(score.scorePercent()).isEqualByComparingTo("66.7");
		assertThat(score.pointsAwarded()).isEqualByComparingTo("0.05");
		assertThat(score.passed()).isTrue();
	}

	@Test
	@DisplayName("returns no score at all when an infrastructure error took part")
	void returnsNullForInfrastructureError() {
		List<TestOutcome> outcomes = List.of(TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED,
				TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.PASSED, TestOutcome.FAILED,
				TestOutcome.FAILED, TestOutcome.INFRASTRUCTURE_ERROR);

		GradingScore score = GradingScorer.score(outcomes, BigDecimal.valueOf(100), BigDecimal.valueOf(100));

		assertThat(score).isNull();
	}

}
