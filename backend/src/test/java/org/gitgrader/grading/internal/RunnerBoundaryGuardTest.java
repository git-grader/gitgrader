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

import java.time.Duration;

import org.gitgrader.configuration.GradingProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Both halves of the boundary fail silently when misconfigured, so both fail at startup.
 */
class RunnerBoundaryGuardTest {

	@Test
	@DisplayName("a runner with no secret refuses to start")
	void refusesAnOpenRunner() {
		// Otherwise the Docker socket is offered to anything that reaches the internal
		// network, and the operator sees an instance that works perfectly.
		assertThatThrownBy(() -> guard("docker", true, "", "").afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("grading.runner-api.secret");
	}

	@Test
	@DisplayName("a web tier with no runner url refuses to start")
	void refusesAWebTierThatCannotGrade() {
		assertThatThrownBy(() -> guard("remote", false, "", "secret").afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("grading.runner-api.url");
	}

	@Test
	@DisplayName("a web tier with no secret refuses to start")
	void refusesAWebTierThatWouldBeTurnedAway() {
		assertThatThrownBy(() -> guard("remote", false, "http://runner:8080", "").afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("grading.runner-api.secret");
	}

	@Test
	@DisplayName("accepts a configured pair, and the single-container default")
	void acceptsWorkingConfigurations() {
		assertThatCode(() -> guard("remote", false, "http://runner:8080", "secret").afterPropertiesSet())
			.doesNotThrowAnyException();
		assertThatCode(() -> guard("docker", true, "", "secret").afterPropertiesSet()).doesNotThrowAnyException();
		assertThatCode(() -> guard("docker", false, "", "").afterPropertiesSet()).doesNotThrowAnyException();
	}

	private static RunnerBoundaryGuard guard(String runner, boolean serving, String url, String secret) {
		return new RunnerBoundaryGuard(new GradingProperties(runner, 2, Duration.ofSeconds(120),
				DataSize.ofMegabytes(512), 1.0, 256, false, DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.RunnerApi(serving, url, secret, Duration.ofSeconds(10), Duration.ofSeconds(30)),
				new GradingProperties.Queue(true, Duration.ofSeconds(2), Duration.ofMinutes(15), 3,
						Duration.ofSeconds(30), 3, 500, 1000, Duration.ofSeconds(30))));
	}

}
