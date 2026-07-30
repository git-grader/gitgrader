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

import org.jspecify.annotations.Nullable;

/**
 * A full view of a test result that an instructor is allowed to see.
 *
 * @param visibility PUBLIC or HIDDEN
 * @param category category from manifest
 * @param testName raw test name
 * @param publicName safe label for students
 * @param outcome the parsed outcome
 * @param durationMs the duration of the test
 * @param studentMessage safe message for the student
 * @param internalMessage internal full message/trace
 * @param hint hint from manifest
 */
public record InstructorTestResultView(String visibility, @Nullable String category, String testName,
		@Nullable String publicName, TestOutcome outcome, @Nullable Long durationMs, @Nullable String studentMessage,
		@Nullable String internalMessage, @Nullable String hint) {
}
