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
import java.util.List;

import org.jspecify.annotations.Nullable;

/** Instructor-visible facts from the latest attempt at a submission. */
public record InstructorGradingResult(int attempt, GradingRunStatus status, int testsPassed, int testsTotal,
		@Nullable BigDecimal scorePercent, @Nullable BigDecimal pointsAwarded, @Nullable Boolean passed,
		@Nullable Instant finishedAt, List<InstructorTestResult> tests) {

	public InstructorGradingResult {
		tests = List.copyOf(tests);
	}

}
