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

package org.gitgrader.security.web;

import org.gitgrader.testsupport.EnabledIfDockerAvailable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the runner API chain accepts the peer the runner client actually is.
 *
 * <p>
 * The chain on {@code /internal/**} serves one caller: the web tier's
 * {@code RemoteGradingRunner}, which authenticates with the shared secret header alone
 * and is not a browser - it reads no cookie and can never know a CSRF token. Any CSRF
 * check on this chain therefore rejects that caller before its secret is even looked at,
 * and every grading run in the remote deployment fails.
 *
 * <p>
 * The request here is shaped exactly like the one the runner client sends: a POST with
 * the secret header and a JSON body, and nothing else.
 */
@SpringBootTest(properties = { "grading.runner-api.enabled=true", "grading.runner-api.secret=a-long-shared-secret" })
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
// The context is closed with the class, while this container is still up. Left cached,
// it outlived the database it was pointed at and every shutdown hook then blocked for
// the full connection timeout, which is what made the JVM miss its own exit.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RunnerApiCsrfIT {

	private static final String SECRET = "a-long-shared-secret";

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.2-alpine")
		.withDatabaseName("gitgrader")
		.withUsername("gitgrader")
		.withPassword("gitgrader");

	@Autowired
	private MockMvc mockMvc;

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Test
	@DisplayName("admits the secret-bearing run request the runner client sends")
	void admitsTheRunRequestTheRunnerClientSends() throws Exception {
		// The grading run never starts - the runner stub answers nothing - but the
		// filter chain must let the request through to the controller, which is where
		// the secret is checked. Anything before that, a 403 or a redirect, means CSRF
		// is refusing the only caller this chain has.
		this.mockMvc
			.perform(post("/internal/grading/runs").header("X-GitGrader-Runner-Secret", SECRET)
				.contentType(MediaType.APPLICATION_JSON)
				.content(
						"""
								{"workspaceDirectory":"./target/test-data/tmp/run-1","hiddenTestsDirectory":"./target/test-data/tests/suite",
								 "runtimeImageDigest":"sha256:abc","installCommand":null,"testCommand":"npm test",
								 "timeout":"PT60S","memoryLimitBytes":268435456,"cpuLimit":1.0,"pidLimit":128,
								 "networkEnabled":false,"logSizeLimitBytes":524288,"correlationId":"cid",
								 "environment":{},"shimKind":null,"shimCommand":null}
								"""))
			.andExpect(status().isOk());
	}

}
