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
 * A safe view of a test result that a student is allowed to see. This object is
 * structurally incapable of carrying hidden secret fields.
 *
 * @param visibility PUBLIC or HIDDEN
 * @param category category from manifest for HIDDEN tests
 * @param publicName safe label for students (for PUBLIC tests)
 * @param outcome the parsed outcome
 * @param durationMs the duration of the test
 * @param studentMessage safe message for the student (for PUBLIC tests)
 * @param hint hint from manifest for HIDDEN tests
 */
public record StudentTestResultView(String visibility, @Nullable String category, @Nullable String publicName,
		TestOutcome outcome, @Nullable Long durationMs, @Nullable String studentMessage, @Nullable String hint) {
}
