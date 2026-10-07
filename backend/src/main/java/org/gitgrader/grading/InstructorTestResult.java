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
 * Safe test facts that never include hidden test names or internal messages.
 *
 * @param visibility whether the test is visible to students
 * @param category suite grouping label, when the suite assigns one
 * @param publicName name as the student may see it, or {@code null} for a hidden test
 * @param outcome how the test finished
 * @param durationMs wall-clock duration in milliseconds, when measured
 * @param studentMessage message safe to show a student, or {@code null} when none applies
 */
public record InstructorTestResult(String visibility, @Nullable String category, @Nullable String publicName,
		TestOutcome outcome, @Nullable Long durationMs, @Nullable String studentMessage) {
}
