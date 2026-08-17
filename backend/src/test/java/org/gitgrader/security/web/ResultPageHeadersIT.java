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
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * Verifies the security headers actually served on the public result page.
 *
 * <p>
 * <strong>Why this is an integration test.</strong> An earlier version of this check
 * built its own {@code HeaderWriterFilter} and asserted against that. It passed, but it
 * would have kept passing if {@code WebSecurityConfig} had been deleted outright: it was
 * testing a fixture, not the product. Since the point is to prove that a real request to
 * a real URL comes back with these headers, the test has to go through the real filter
 * chain, which means a real application context.
 *
 * <p>
 * The result page is reachable without any login - the unguessable token is the only
 * credential - so these headers are the entire defence against the link leaking through a
 * referrer, being indexed by a crawler, or being framed by another site.
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
class ResultPageHeadersIT {

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
	@DisplayName("serves the hardened headers on a result URL")
	void resultPageCarriesHardenedHeaders() throws Exception {
		// Asserted on the headers rather than the body: whether this particular token
		// resolves is irrelevant, the filter chain must harden the response either way.
		this.mockMvc.perform(get("/result/some-unguessable-token"))
			.andExpect(header().string("X-Content-Type-Options", "nosniff"))
			.andExpect(header().string("Referrer-Policy", "no-referrer"))
			.andExpect(header().string("X-Robots-Tag", "noindex, nofollow"))
			.andExpect(header().exists("Content-Security-Policy"));
	}

	@Test
	@DisplayName("applies the stricter result CSP, not the application-wide one")
	void resultPageUsesTheStricterPolicy() throws Exception {
		// The app shell needs 'self' scripts; the result page does not, and defaulting to
		// the looser application policy here would quietly widen the blast radius of any
		// injection on the one page an unauthenticated stranger can open.
		this.mockMvc.perform(get("/result/some-unguessable-token"))
			.andExpect(header().string("Content-Security-Policy",
					org.hamcrest.Matchers.containsString("default-src 'none'")));
	}

	@Test
	@DisplayName("refuses to be framed by another site")
	void resultPageCannotBeFramed() throws Exception {
		this.mockMvc.perform(get("/result/some-unguessable-token"))
			.andExpect(header().string("X-Frame-Options", "DENY"));
	}

}
