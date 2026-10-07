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
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Summary columns from the latest grading run for one submission.
 *
 * @param submissionId submission these figures describe
 * @param attempt one-based attempt number for this submission
 * @param status status of the grading run
 * @param testsPassed tests that passed
 * @param testsTotal tests run
 * @param scorePercent percentage of tests passed, or {@code null} before scoring
 * @param pointsAwarded points earned, or {@code null} before scoring
 * @param passed whether the attempt met the pass threshold, or {@code null} before
 * scoring
 * @param finishedAt instant the run finished, or {@code null} while it is still running
 */
public record SubmissionScoreView(UUID submissionId, int attempt, GradingRunStatus status, int testsPassed,
		int testsTotal, @Nullable BigDecimal scorePercent, @Nullable BigDecimal pointsAwarded, @Nullable Boolean passed,
		@Nullable Instant finishedAt) {
}
