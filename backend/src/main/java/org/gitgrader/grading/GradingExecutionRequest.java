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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.jspecify.annotations.Nullable;

/**
 * The specification for a grading run.
 *
 * @param workspaceDirectory the directory containing student code to mount at /workspace
 * @param hiddenTestsDirectory the directory containing hidden tests to mount read-only
 * @param runtimeImageDigest the immutable digest of the container image to run
 * @param installCommand the command to run to install dependencies, if any
 * @param testCommand the command to run the tests and produce output
 * @param timeout the maximum duration the runner should allow before killing it
 * @param memoryLimitBytes the maximum memory in bytes
 * @param cpuLimit the maximum cpu cores
 * @param pidLimit the maximum number of pids (fork limit)
 * @param networkEnabled whether the container should have network access
 * @param logSizeLimitBytes the maximum size of log output to capture in bytes
 * @param correlationId the correlation identifier for logging
 * @param environment any environment variables to set
 */
public record GradingExecutionRequest(Path workspaceDirectory, Path hiddenTestsDirectory, String runtimeImageDigest,
		@Nullable String installCommand, String testCommand, Duration timeout, long memoryLimitBytes, double cpuLimit,
		int pidLimit, boolean networkEnabled, long logSizeLimitBytes, String correlationId,
		Map<String, String> environment) {
}
