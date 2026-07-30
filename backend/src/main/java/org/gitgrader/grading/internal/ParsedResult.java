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

import org.gitgrader.grading.TestOutcome;
import org.jspecify.annotations.Nullable;

/**
 * A single parsed and classified test result.
 *
 * @param visibility PUBLIC or HIDDEN
 * @param category category from manifest
 * @param testName raw test name
 * @param publicName safe label for students
 * @param outcome the parsed outcome
 * @param weight the weight of the test
 * @param durationMs the duration of the test
 * @param studentMessage safe message for the student
 * @param internalMessage internal full message/trace
 * @param hint hint from manifest
 */
public record ParsedResult(String visibility, @Nullable String category, String testName, @Nullable String publicName,
		TestOutcome outcome, BigDecimal weight, @Nullable Long durationMs, @Nullable String studentMessage,
		@Nullable String internalMessage, @Nullable String hint) {
}
