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
import org.gitgrader.grading.GradingResult;
import org.gitgrader.grading.GradingRunner;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The runner's whole surface: one operation, and who is allowed to ask for it.
 */
class GradingRunnerApiControllerTest {

	private static final String SECRET = "a-long-shared-secret";

	private static final ObjectMapper MAPPER = new ObjectMapper();

	private final GradingRunner runner = mock(GradingRunner.class);

	@Test
	@DisplayName("grades for a caller that presents the secret")
	void gradesForAnAuthenticatedCaller() throws Exception {
		when(this.runner.execute(any())).thenReturn(new GradingResult(0, "ok", "", 42L, false, false, null));

		mockMvc()
			.perform(post("/internal/grading/runs").header(GradingRunnerApiController.SECRET_HEADER, SECRET)
				.contentType("application/json")
				.content(body()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.exitCode").value(0))
			.andExpect(jsonPath("$.stdout").value("ok"));
	}

	@Test
	@DisplayName("refuses a caller with the wrong secret, and starts nothing")
	void refusesTheWrongSecret() throws Exception {
		mockMvc()
			.perform(post("/internal/grading/runs").header(GradingRunnerApiController.SECRET_HEADER, "not-the-secret")
				.contentType("application/json")
				.content(body()))
			.andExpect(status().isUnauthorized());

		// The point of the check: no container is created for an unauthenticated caller.
		verify(this.runner, never()).execute(any());
	}

	@Test
	@DisplayName("refuses a caller presenting no secret at all")
	void refusesNoSecret() throws Exception {
		mockMvc().perform(post("/internal/grading/runs").contentType("application/json").content(body()))
			.andExpect(status().isUnauthorized());

		verify(this.runner, never()).execute(any());
	}

	@Test
	@DisplayName("refuses a path outside the volumes even from an authenticated caller")
	void refusesAnEscapingPath() throws Exception {
		String escaping = new ObjectMapper().writeValueAsString(request(Path.of("/etc"), Path.of("/data/tests/suite")));

		mockMvc()
			.perform(post("/internal/grading/runs").header(GradingRunnerApiController.SECRET_HEADER, SECRET)
				.contentType("application/json")
				.content(escaping))
			.andExpect(status().isBadRequest());

		verify(this.runner, never()).execute(any());
	}

	private MockMvc mockMvc() {
		return MockMvcBuilders
			.standaloneSetup(new GradingRunnerApiController(this.runner, properties(),
					new StorageProperties("/data/git/repositories", "/data/templates", "/data/tests", "/data/artifacts",
							"/data/grading")))
			.build();
	}

	private static String body() throws Exception {
		return new ObjectMapper()
			.writeValueAsString(request(Path.of("/data/grading/run-1"), Path.of("/data/tests/suite")));
	}

	private static GradingExecutionRequest request(Path workspace, Path hiddenTests) {
		return new GradingExecutionRequest(workspace, hiddenTests, "sha256:abc", null, "npm test",
				Duration.ofSeconds(60), DataSize.ofMegabytes(256).toBytes(), 1.0, 128, false,
				DataSize.ofKilobytes(512).toBytes(), "cid", Map.of());
	}

	private static GradingProperties properties() {
		return new GradingProperties("docker", 2, Duration.ofSeconds(120), DataSize.ofMegabytes(512), 1.0, 256, false,
				DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.RunnerApi(true, "", SECRET, Duration.ofSeconds(10), Duration.ofSeconds(30)),
				new GradingProperties.Queue(true, Duration.ofSeconds(2), Duration.ofMinutes(15), 3,
						Duration.ofSeconds(30), 3, 500, 1000, Duration.ofSeconds(30)));
	}

}
