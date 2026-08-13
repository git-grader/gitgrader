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

package org.gitgrader.testsupport;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Decides whether a Docker-backed test can run here.
 *
 * @see EnabledIfDockerAvailable
 */
class DockerAvailableCondition implements ExecutionCondition {

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		if (dockerAvailable()) {
			return ConditionEvaluationResult.enabled("Docker is available");
		}
		if (runningOnCi()) {
			// Enabled on purpose, so the run fails on the missing engine rather than
			// reporting a green build that proved nothing.
			return ConditionEvaluationResult.enabled("No Docker engine, but CI must not skip integration tests");
		}
		return ConditionEvaluationResult.disabled("No Docker engine is reachable",
				"Start Docker to run the integration tests, or use -DskipITs to leave them out deliberately.");
	}

	private static boolean dockerAvailable() {
		try {
			return DockerClientFactory.instance().isDockerAvailable();
		}
		catch (RuntimeException ex) {
			return false;
		}
	}

	private static boolean runningOnCi() {
		return System.getenv("CI") != null;
	}

}
