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
 * The raw result of executing the tests in the sandbox.
 *
 * @param exitCode the exit code of the test command
 * @param stdout the captured standard output
 * @param stderr the captured standard error
 * @param durationMillis the wall-clock execution time in milliseconds
 * @param timedOut whether the execution was terminated due to timeout
 * @param infrastructureFailure whether an infrastructure error prevented execution
 * @param failureDetail human-readable details about an infrastructure failure
 */
public record GradingResult(int exitCode, String stdout, String stderr, long durationMillis, boolean timedOut,
		boolean infrastructureFailure, @Nullable String failureDetail) {
}
