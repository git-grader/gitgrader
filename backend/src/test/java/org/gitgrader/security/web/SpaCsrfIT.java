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

import jakarta.servlet.http.Cookie;
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
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies that the single-page application can obtain and use a CSRF token.
 *
 * <p>
 * <strong>Why this is an integration test.</strong> Both halves of the mechanism are
 * filter-chain wiring, and neither is observable from a unit test: one issues the cookie,
 * the other decides which submitted values are acceptable. Only an assembled context puts
 * them in the same request.
 *
 * <p>
 * The bug this pins down made every write from the browser fail. Token loading is
 * deferred, so nothing wrote the cookie for a client that renders no form, and the
 * default handler masks the token, so the cookie value would have been rejected even if
 * it had been sent. Together those made the entire instructor interface unusable while
 * every existing test still passed.
 *
 * <p>
 * The assertions lean on ordering: CSRF is checked before authorization, so a rejected
 * token is answered with 403 and an accepted one falls through to the 401 that says the
 * caller simply is not signed in. That difference is what tells the two failures apart
 * without needing a directory to log in against.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
// The context is closed with the class, while this container is still up. Left cached,
// it outlived the database it was pointed at and every shutdown hook then blocked for
// the full connection timeout, which is what made the JVM miss its own exit.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class SpaCsrfIT {

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
	@DisplayName("hands the application a CSRF cookie on an ordinary read")
	void issuesCsrfCookieOnRead() throws Exception {
		MvcResult result = this.mockMvc.perform(get("/api/v1/meta")).andReturn();

		Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookie).as("the application has no way to obtain a token without this cookie").isNotNull();
		assertThat(cookie.getValue()).isNotBlank();
		assertThat(cookie.isHttpOnly()).as("script has to read it, so it cannot be http-only").isFalse();
	}

	@Test
	@DisplayName("accepts the cookie value submitted in the header")
	void acceptsCookieValueFromHeader() throws Exception {
		Cookie csrf = this.mockMvc.perform(get("/api/v1/meta")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();

		this.mockMvc
			.perform(post("/api/v1/courses").cookie(csrf)
				.header("X-XSRF-TOKEN", csrf.getValue())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@DisplayName("still rejects a write that carries no token")
	void rejectsWriteWithoutToken() throws Exception {
		this.mockMvc.perform(post("/api/v1/courses").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("answers a refused write with a problem document rather than a redirect")
	void refusedWriteIsAProblemDocument() throws Exception {
		// The default handler redirects to the sign-in page. The SPA posts with fetch,
		// which follows that transparently and then parses a login page as its answer,
		// and
		// the redirect carried the session identifier in its path.
		this.mockMvc.perform(post("/api/v1/registration").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isForbidden())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.status").value(403));
	}

	@Test
	@DisplayName("challenges an unauthenticated actuator scrape instead of redirecting it")
	void actuatorChallengesRatherThanRedirects() throws Exception {
		// Prometheus follows a redirect and stores the sign-in page as the metrics
		// response, so the scrape looks successful and reports nothing.
		this.mockMvc.perform(get("/actuator/metrics"))
			.andExpect(status().isUnauthorized())
			.andExpect(header().string("WWW-Authenticate", org.hamcrest.Matchers.containsString("Basic")));
	}

	@Test
	@DisplayName("still rejects a write whose token does not match the cookie")
	void rejectsMismatchedToken() throws Exception {
		Cookie csrf = this.mockMvc.perform(get("/api/v1/meta")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(csrf).isNotNull();

		this.mockMvc
			.perform(post("/api/v1/courses").cookie(csrf)
				.header("X-XSRF-TOKEN", "not-the-value-from-the-cookie")
				.contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
			.andExpect(status().isForbidden());
	}

}
