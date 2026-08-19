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

import org.gitgrader.configuration.GradingProperties;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.stereotype.Component;

/**
 * Refuses to start either half of the grading boundary in a state that is not a boundary.
 *
 * <p>
 * Both failures are silent ones. A runner with no secret accepts a run from anything that
 * reaches the internal network, and the operator sees a working instance. A web tier with
 * no runner URL cannot grade at all, and the first sign of it is a student's push being
 * accepted and never scored.
 */
@Component
class RunnerBoundaryGuard implements InitializingBean {

	private final GradingProperties properties;

	RunnerBoundaryGuard(GradingProperties properties) {
		this.properties = properties;
	}

	@Override
	public void afterPropertiesSet() {
		GradingProperties.RunnerApi api = this.properties.runnerApi();
		boolean serving = api.enabled();
		boolean calling = "remote".equals(this.properties.runner());

		if (serving && api.secret().isBlank()) {
			throw new IllegalStateException("grading.runner-api.secret must be set when the runner API is enabled: "
					+ "without it the Docker socket is offered to anything that can reach this service.");
		}
		if (calling && api.url().isBlank()) {
			throw new IllegalStateException(
					"grading.runner-api.url must be set when grading.runner is 'remote', or nothing can be graded.");
		}
		if (calling && api.secret().isBlank()) {
			throw new IllegalStateException(
					"grading.runner-api.secret must be set when grading.runner is 'remote'; the runner will refuse "
							+ "every run without it.");
		}
	}

}
