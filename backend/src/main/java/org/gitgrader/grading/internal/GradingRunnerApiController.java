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

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.gitgrader.grading.GradingResult;
import org.gitgrader.grading.GradingRunner;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The one operation the process holding the Docker socket will perform for anybody.
 *
 * <p>
 * This is the whole of the runner's surface: no container listing, no image management,
 * no arbitrary create. A caller says "grade this", and what it is allowed to influence is
 * narrowed by {@link RunnerRequestGuard} before a container exists. That is what makes
 * this a boundary rather than a thinner way to reach the same daemon.
 *
 * <p>
 * Served only by the runner role, which is the only container with the socket, and
 * published on no host port. The secret keeps anything else that reaches the internal
 * network from asking for a run.
 */
@RestController
@RequestMapping("/internal/grading")
@ConditionalOnProperty(name = "grading.runner-api.enabled", havingValue = "true")
public class GradingRunnerApiController {

	/**
	 * Named as a header rather than a bearer token: it authenticates a peer, not a user.
	 */
	public static final String SECRET_HEADER = "X-GitGrader-Runner-Secret";

	private static final Logger logger = LoggerFactory.getLogger(GradingRunnerApiController.class);

	private final GradingRunner runner;

	private final RunnerRequestGuard guard;

	private final byte[] secret;

	public GradingRunnerApiController(GradingRunner runner, GradingProperties properties, StorageProperties storage) {
		this.runner = runner;
		this.guard = new RunnerRequestGuard(properties, storage);
		this.secret = properties.runnerApi().secret().getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * Runs one submission in a throwaway sandbox.
	 * @param presented the secret the caller offers
	 * @param request what the caller wants graded
	 * @return the raw result, or a status the caller can tell apart from a failed run
	 */
	@PostMapping("/runs")
	public ResponseEntity<GradingResult> run(
			@RequestHeader(name = SECRET_HEADER, required = false) @Nullable String presented,
			@RequestBody GradingExecutionRequest request) {
		if (!authenticated(presented)) {
			logger.warn("Rejected a grading run request with a missing or wrong secret");
			return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
		}

		GradingExecutionRequest sanitised;
		try {
			sanitised = this.guard.sanitise(request);
		}
		catch (IllegalArgumentException refused) {
			// The caller is the web tier, so this is either a bug or a web tier that is
			// no longer being driven by GitGrader. Both are worth a loud line.
			logger.error("Refused a grading run request: {}", refused.getMessage());
			return ResponseEntity.badRequest().build();
		}

		return ResponseEntity.ok(this.runner.execute(sanitised));
	}

	private boolean authenticated(@Nullable String presented) {
		if (presented == null || this.secret.length == 0) {
			return false;
		}
		return MessageDigest.isEqual(this.secret, presented.getBytes(StandardCharsets.UTF_8));
	}

}
