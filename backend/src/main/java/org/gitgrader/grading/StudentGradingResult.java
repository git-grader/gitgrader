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
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * What a student is allowed to be told about their most recent grading run.
 *
 * <p>
 * Everything here has already passed through redaction. A hidden check contributes its
 * outcome and its hint but never its name or the assertion it failed on, so the result
 * page can be served to whoever holds the link without leaking the test suite.
 *
 * @param status how the run ended
 * @param testsPassed how many checks passed
 * @param testsTotal how many checks ran
 * @param scorePercent the percentage recorded for the run, absent while it is unfinished
 * @param passed whether the run met the assignment's threshold, absent while unfinished
 * @param tests the per-check outcomes, in the order they should be displayed
 */
public record StudentGradingResult(GradingRunStatus status, int testsPassed, int testsTotal,
		@Nullable BigDecimal scorePercent, @Nullable Boolean passed, List<StudentTestResultView> tests) {

	public StudentGradingResult {
		tests = List.copyOf(tests);
	}
}
