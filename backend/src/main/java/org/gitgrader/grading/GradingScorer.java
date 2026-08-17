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
import java.math.RoundingMode;
import java.util.List;
import org.jspecify.annotations.Nullable;

/**
 * Calculates a score from a list of test outcomes.
 */
public final class GradingScorer {

	private GradingScorer() {
		// Utility class
	}

	/**
	 * Calculates the score based on the test outcomes, max points, and pass threshold.
	 *
	 * <p>
	 * An {@code INFRASTRUCTURE_ERROR} outcome in any test must NEVER be counted as a
	 * failed test. Instead, the entire run produces no score.
	 * @param outcomes the list of individual test outcomes
	 * @param maxPoints the maximum possible points for the assignment
	 * @param passThreshold the minimum percentage required to pass
	 * @return the calculated grading score, or null if an infrastructure error occurred
	 */
	public static @Nullable GradingScore score(List<TestOutcome> outcomes, BigDecimal maxPoints,
			BigDecimal passThreshold) {
		int passedCount = 0;
		int failedCount = 0;
		int erroredCount = 0;
		int skippedCount = 0;

		for (TestOutcome outcome : outcomes) {
			if (outcome == TestOutcome.INFRASTRUCTURE_ERROR) {
				// One infrastructure error invalidates the whole run. Scoring the
				// remaining tests would report a lower grade for a failure that was not
				// the student's, which the brief forbids outright.
				return null;
			}
			switch (outcome) {
				case PASSED -> passedCount++;
				case FAILED, TIMEOUT -> failedCount++;
				case NOT_EXECUTED -> skippedCount++;
				default -> erroredCount++;
			}
		}

		int total = outcomes.size();
		if (total == 0) {
			return new GradingScore(0, 0, 0, 0, 0, BigDecimal.ZERO, BigDecimal.ZERO, false);
		}

		BigDecimal scorePercent = BigDecimal.valueOf(passedCount)
			.multiply(BigDecimal.valueOf(100))
			.divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP);

		BigDecimal pointsAwarded = maxPoints.multiply(BigDecimal.valueOf(passedCount))
			.divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);

		boolean passed = scorePercent.compareTo(passThreshold) >= 0;

		return new GradingScore(total, passedCount, failedCount, erroredCount, skippedCount, scorePercent,
				pointsAwarded, passed);
	}

	/**
	 * Calculates the score based on the test outcomes. Default max points is 100, default
	 * pass threshold is 100.
	 * @param outcomes the list of individual test outcomes
	 * @return the calculated grading score, or null if an infrastructure error occurred
	 */
	public static @Nullable GradingScore score(List<TestOutcome> outcomes) {
		return score(outcomes, BigDecimal.valueOf(100), BigDecimal.valueOf(100));
	}

}
