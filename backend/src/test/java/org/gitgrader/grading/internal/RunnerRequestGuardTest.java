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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;

import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * What the runner will do for a caller that has been taken over.
 *
 * <p>
 * Moving the Docker socket out of the web application is only worth something if the one
 * operation left is narrow. These are the things a compromised caller would try.
 */
class RunnerRequestGuardTest {

	private final RunnerRequestGuard guard = new RunnerRequestGuard(properties(), new StorageProperties(
			"/data/git/repositories", "/data/templates", "/data/tests", "/data/artifacts", "/data/grading"));

	@Test
	@DisplayName("refuses a workspace outside the volume")
	void refusesWorkspaceEscape() {
		assertThatThrownBy(() -> this.guard.sanitise(request(Path.of("/etc"), Path.of("/data/tests/suite"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("workspace");
	}

	@Test
	@DisplayName("refuses hidden tests outside the volume, traversal included")
	void refusesTestsEscape() {
		assertThatThrownBy(
				() -> this.guard.sanitise(request(Path.of("/data/grading/run-1"), Path.of("/data/tests/../../etc"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("hidden tests");
	}

	@Test
	@DisplayName("clamps every limit to the runner's own ceiling")
	void clampsLimits() {
		GradingExecutionRequest greedy = new GradingExecutionRequest(Path.of("/data/grading/run-1"),
				Path.of("/data/tests/suite"), "sha256:abc", null, "npm test", Duration.ofHours(9),
				DataSize.ofGigabytes(64).toBytes(), 32.0, 100_000, true, DataSize.ofGigabytes(4).toBytes(), "cid",
				Map.of());

		GradingExecutionRequest allowed = this.guard.sanitise(greedy);

		assertThat(allowed.timeout()).isEqualTo(Duration.ofSeconds(120));
		assertThat(allowed.memoryLimitBytes()).isEqualTo(DataSize.ofMegabytes(512).toBytes());
		assertThat(allowed.cpuLimit()).isEqualTo(1.0);
		assertThat(allowed.pidLimit()).isEqualTo(256);
		assertThat(allowed.logSizeLimitBytes()).isEqualTo(DataSize.ofMegabytes(1).toBytes());
	}

	@Test
	@DisplayName("never hands a sandbox the network the runner was configured to withhold")
	void neverWidensNetwork() {
		GradingExecutionRequest asking = request(Path.of("/data/grading/run-1"), Path.of("/data/tests/suite"));

		assertThat(this.guard.sanitise(asking).networkEnabled()).isFalse();
	}

	@Test
	@DisplayName("leaves a modest request as it was")
	void leavesReasonableRequestAlone() {
		GradingExecutionRequest modest = new GradingExecutionRequest(Path.of("/data/grading/run-1"),
				Path.of("/data/tests/suite"), "sha256:abc", "npm ci", "npm test", Duration.ofSeconds(30),
				DataSize.ofMegabytes(128).toBytes(), 0.5, 64, false, DataSize.ofKilobytes(256).toBytes(), "cid",
				Map.of("CI", "true"));

		GradingExecutionRequest allowed = this.guard.sanitise(modest);

		assertThat(allowed.timeout()).isEqualTo(Duration.ofSeconds(30));
		assertThat(allowed.memoryLimitBytes()).isEqualTo(DataSize.ofMegabytes(128).toBytes());
		assertThat(allowed.cpuLimit()).isEqualTo(0.5);
		assertThat(allowed.installCommand()).isEqualTo("npm ci");
		assertThat(allowed.environment()).containsEntry("CI", "true");
	}

	private static GradingExecutionRequest request(Path workspace, Path hiddenTests) {
		return new GradingExecutionRequest(workspace, hiddenTests, "sha256:abc", null, "npm test",
				Duration.ofSeconds(60), DataSize.ofMegabytes(256).toBytes(), 1.0, 128, true,
				DataSize.ofKilobytes(512).toBytes(), "cid", Map.of());
	}

	private static GradingProperties properties() {
		return new GradingProperties("docker", 2, Duration.ofSeconds(120), DataSize.ofMegabytes(512), 1.0, 256, false,
				DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.RunnerApi(true, "", "secret", Duration.ofSeconds(10), Duration.ofSeconds(30)),
				new GradingProperties.Queue(true, Duration.ofSeconds(2), Duration.ofMinutes(15), 3,
						Duration.ofSeconds(30), 3, 500, 1000, Duration.ofSeconds(30)));
	}

}
