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

/**
 * Executes untrusted student code in a sandbox and returns the captured output.
 */
public interface GradingRunner {

	/**
	 * Executes a grading run request inside a sandbox.
	 * @param request the parameters for the run
	 * @return the raw output and status of the execution
	 * @throws GradingRunnerException if the runner itself completely breaks
	 */
	GradingResult execute(GradingExecutionRequest request);

}
