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

import java.time.Clock;

import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.gitgrader.grading.GradingResult;
import org.gitgrader.grading.GradingRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Grades by asking the runner service, because this process cannot reach Docker.
 *
 * <p>
 * The web tier used to hold the Docker socket, which is host root: any compromise of it
 * could start a privileged container mounting the whole filesystem. It now has no route
 * to the daemon at all, and asks a service that will only ever do one thing for it.
 *
 * <p>
 * A runner that cannot be reached is an infrastructure failure, never a score. Returning
 * zero when the runner is down would be indistinguishable from a student who passed
 * nothing, and would be recorded against them permanently.
 */
@Component
@ConditionalOnProperty(name = "grading.runner", havingValue = "remote")
class RemoteGradingRunner implements GradingRunner {

	private static final Logger logger = LoggerFactory.getLogger(RemoteGradingRunner.class);

	private final RestClient client;

	private final Clock clock;

	private final String secret;

	// Annotated because there are two constructors and the other one exists for tests to
	// hand in a stubbed transport; without it the container cannot tell which to call.
	@Autowired
	RemoteGradingRunner(GradingProperties properties, Clock clock) {
		// Builds its own client rather than taking an injected builder: there is no
		// RestClient.Builder bean in this application, and discovering that at startup
		// costs an instance that boots into a failed context.
		this(RestClient.builder().requestFactory(requestFactory(properties)), properties, clock);
	}

	RemoteGradingRunner(RestClient.Builder builder, GradingProperties properties, Clock clock) {
		GradingProperties.RunnerApi api = properties.runnerApi();
		this.client = builder.baseUrl(api.url()).build();
		this.clock = clock;
		this.secret = api.secret();
	}

	/**
	 * Waits longer for an answer than a run is allowed to take.
	 *
	 * <p>
	 * The runner is the one that decides a run has overrun, and it kills the sandbox and
	 * reports a timeout. If the caller gave up first the run would be recorded as
	 * unreachable infrastructure while the sandbox was still going, and the student would
	 * be told the grader broke when it was about to answer.
	 * @param properties where the timeouts come from
	 * @return a request factory bounded on both connecting and reading
	 */
	private static ClientHttpRequestFactory requestFactory(GradingProperties properties) {
		GradingProperties.RunnerApi api = properties.runnerApi();
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout((int) api.connectTimeout().toMillis());
		factory.setReadTimeout((int) properties.defaultTimeout().plus(api.responseMargin()).toMillis());
		return factory;
	}

	@Override
	public GradingResult execute(GradingExecutionRequest request) {
		long start = this.clock.millis();
		try {
			GradingResult result = this.client.post()
				.uri("/internal/grading/runs")
				.contentType(MediaType.APPLICATION_JSON)
				.header(GradingRunnerApiController.SECRET_HEADER, this.secret)
				.body(request)
				.retrieve()
				.body(GradingResult.class);

			if (result == null) {
				return unusable(start, "The runner answered the grading request with an empty body");
			}
			return result;
		}
		catch (RestClientException ex) {
			logger.error("Could not reach the grading runner [correlationId={}]", request.correlationId(), ex);
			return unusable(start, "The grading runner could not be reached: " + ex.getMessage());
		}
	}

	private GradingResult unusable(long start, String detail) {
		return new GradingResult(-1, "", "", this.clock.millis() - start, false, true, detail);
	}

}
