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
import java.time.Clock;
import java.time.Duration;
import java.util.Map;

import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.gitgrader.grading.GradingResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.util.unit.DataSize;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * The web tier's half of the boundary.
 */
class RemoteGradingRunnerTest {

	private static final String SECRET = "a-long-shared-secret";

	@Test
	@DisplayName("presents the secret and returns what the runner reported")
	void gradesThroughTheRunner() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://runner:8080/internal/grading/runs"))
			.andExpect(method(org.springframework.http.HttpMethod.POST))
			.andExpect(header(GradingRunnerApiController.SECRET_HEADER, SECRET))
			.andRespond(withSuccess("""
					{"exitCode":0,"stdout":"1..1\\nok 1 adds","stderr":"","durationMillis":42,
					 "timedOut":false,"infrastructureFailure":false,"failureDetail":null}
					""", MediaType.APPLICATION_JSON));

		GradingResult result = runner(builder).execute(request());

		assertThat(result.exitCode()).isZero();
		assertThat(result.stdout()).contains("ok 1 adds");
		assertThat(result.infrastructureFailure()).isFalse();
		server.verify();
	}

	@Test
	@DisplayName("a runner that cannot be reached is an infrastructure failure, never a zero")
	void treatsAnUnreachableRunnerAsInfrastructure() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("http://runner:8080/internal/grading/runs")).andRespond(withServerError());

		GradingResult result = runner(builder).execute(request());

		// Scoring zero here would be indistinguishable from a student who passed nothing,
		// and it would be recorded against them permanently.
		assertThat(result.infrastructureFailure()).isTrue();
		assertThat(result.failureDetail()).contains("runner");
	}

	private static RemoteGradingRunner runner(RestClient.Builder builder) {
		return new RemoteGradingRunner(builder, properties(), Clock.systemUTC());
	}

	private static GradingExecutionRequest request() {
		return new GradingExecutionRequest(Path.of("/data/grading/run-1"), Path.of("/data/tests/suite"), "sha256:abc",
				null, "npm test", Duration.ofSeconds(60), DataSize.ofMegabytes(256).toBytes(), 1.0, 128, false,
				DataSize.ofKilobytes(512).toBytes(), "cid", Map.of());
	}

	private static GradingProperties properties() {
		return new GradingProperties("remote", 2, Duration.ofSeconds(120), DataSize.ofMegabytes(512), 1.0, 256, false,
				DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.RunnerApi(false, "http://runner:8080", SECRET, Duration.ofSeconds(10),
						Duration.ofSeconds(30)),
				new GradingProperties.Queue(true, Duration.ofSeconds(2), Duration.ofMinutes(15), 3,
						Duration.ofSeconds(30), 3, 500, 1000, Duration.ofSeconds(30)));
	}

}
