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

/**
 * The calculated score for a grading run.
 *
 * @param testsTotal the total number of tests
 * @param testsPassed the number of tests that passed
 * @param testsFailed the number of tests that failed
 * @param testsErrored the number of tests that errored
 * @param testsSkipped the number of tests that were skipped
 * @param scorePercent the calculated percentage score
 * @param pointsAwarded the calculated points awarded
 * @param passed whether the score meets the pass threshold
 */
public record GradingScore(int testsTotal, int testsPassed, int testsFailed, int testsErrored, int testsSkipped,
		BigDecimal scorePercent, BigDecimal pointsAwarded, boolean passed) {
}
