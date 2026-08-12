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

package org.gitgrader.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Verifies what an address that no controller maps is answered with.
 *
 * <p>
 * <strong>Why this is an integration test.</strong> The behaviour is the outcome of three
 * things agreeing: which security chain the address selects, whether any handler mapping
 * claims it, and what the static resource chain does when no file matches. None of the
 * three is observable on its own, and the bug this pins down - a reloaded page answered
 * with a JSON 404 - was invisible to every unit test in the suite.
 *
 * <p>
 * The split these assertions defend is the whole point of {@link SpaWebConfiguration}: a
 * page address gets the application shell so the browser's router can render it, while
 * {@code /api}, {@code /actuator} and anything that names a file keep answering with a
 * plain 404. Losing that split in either direction is a real failure - one makes reloads
 * break, the other makes a mistyped API call return HTML that a client parses as JSON.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class SpaRoutingIT {

	/** Present in the built shell and in nothing else that could be served by mistake. */
	private static final String SHELL_MARKER = "<div id=\"root\">";

	// Applied per request rather than through @WithMockUser: the annotation populates a
	// holder that the assembled filter chain in this test never reads, so every request
	// arrived anonymous and the assertions silently measured the sign-in redirect
	// instead.
	private static final org.springframework.test.web.servlet.request.RequestPostProcessor INSTRUCTOR = user(
			"instructor")
		.roles("INSTRUCTOR");

	private static final org.springframework.test.web.servlet.request.RequestPostProcessor ADMIN = user("admin-user")
		.roles("ADMIN");

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
	@DisplayName("answers a client-side route with the application shell")
	void servesTheShellForAClientSideRoute() throws Exception {
		this.mockMvc.perform(get("/submissions/9f1c").with(INSTRUCTOR))
			.andExpect(status().isOk())
			.andExpect(content().contentTypeCompatibleWith("text/html"))
			.andExpect(content().string(containsString(SHELL_MARKER)));
	}

	@Test
	@DisplayName("answers an address the router does not know with the shell, so its own page not found renders")
	void servesTheShellForAnUnknownPage() throws Exception {
		// This is the case the enumerated forwarding controller could not cover: the
		// frontend ships a branded "page not found" screen that was unreachable, because
		// an address nobody had listed was answered with JSON before React ever loaded.
		this.mockMvc.perform(get("/audit").with(INSTRUCTOR))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(SHELL_MARKER)));
	}

	@Test
	@DisplayName("sends an anonymous visitor to sign in rather than handing out the shell")
	void anonymousPageRequestIsStillGuarded() throws Exception {
		// The fallback must not become a way to reach the application without signing in.
		// Chain selection happens before any of this, on the address as requested.
		this.mockMvc.perform(get("/audit")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/login"));
	}

	@Test
	@DisplayName("keeps a mistyped API call answerable as a 404, not as a page")
	void unknownApiPathStaysAJsonProblem() throws Exception {
		this.mockMvc.perform(get("/api/v1/does-not-exist").with(INSTRUCTOR))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString(SHELL_MARKER))));
	}

	@Test
	@DisplayName("keeps an unknown actuator endpoint a 404, not a page")
	void unknownActuatorPathStaysA404() throws Exception {
		this.mockMvc.perform(get("/actuator/does-not-exist").with(ADMIN))
			.andExpect(status().isNotFound())
			.andExpect(content().string(not(containsString(SHELL_MARKER))));
	}

	@Test
	@DisplayName("refuses a missing script instead of answering it with HTML")
	void missingAssetIsNotAnsweredWithTheShell() throws Exception {
		// Answering this with the shell under a 200 would make the browser fail while
		// parsing HTML as JavaScript, a long way from the missing file that caused it.
		this.mockMvc.perform(get("/assets/does-not-exist.js").with(INSTRUCTOR)).andExpect(status().isNotFound());
	}

	@Test
	@DisplayName("still serves a real file that exists")
	void realAssetsAreStillServed() throws Exception {
		this.mockMvc.perform(get("/favicon.ico"))
			.andExpect(status().isOk())
			.andExpect(content().string(not(containsString(SHELL_MARKER))));
	}

	@Test
	@DisplayName("serves the result page anonymously and keeps its stricter policy")
	void resultPageIsServedAnonymouslyWithItsOwnPolicy() throws Exception {
		this.mockMvc.perform(get("/result/some-unguessable-token"))
			.andExpect(status().isOk())
			.andExpect(content().string(containsString(SHELL_MARKER)))
			.andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")))
			.andExpect(header().string("X-Robots-Tag", "noindex, nofollow"));
	}

	@Test
	@DisplayName("lets the shell be revalidated while the hashed bundles stay cacheable")
	void shellIsRevalidatedAndHashedAssetsAreNot() throws Exception {
		// A year-long cache on the one filename that never changes is how a browser ends
		// up running an old application against a new API.
		this.mockMvc.perform(get("/dashboard").with(INSTRUCTOR))
			.andExpect(header().string("Cache-Control", containsString("no-cache")));

		this.mockMvc.perform(get("/favicon.ico"))
			.andExpect(header().string("Cache-Control", containsString("no-cache")));
	}

	@Test
	@DisplayName("still lets the API description be reached")
	void openApiDescriptionIsUnaffected() throws Exception {
		this.mockMvc.perform(get("/api/v1/openapi").with(INSTRUCTOR))
			.andExpect(status().isOk())
			.andExpect(content().string(startsWith("{")));
	}

}
