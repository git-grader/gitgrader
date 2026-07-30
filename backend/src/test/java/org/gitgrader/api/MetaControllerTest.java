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

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Set;

import org.gitgrader.configuration.AppProperties;
import org.gitgrader.configuration.GitProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link MetaController}.
 *
 * <p>
 * This endpoint is the mechanism that keeps the product organization neutral: the
 * frontend renders whatever it returns and hardcodes nothing. So the assertion that
 * matters is not that some name comes back, but that the <em>configured</em> name comes
 * back - a controller returning a constant would satisfy a weaker test and quietly
 * reintroduce the branding this project is required not to have.
 */
class MetaControllerTest {

	@Test
	@DisplayName("returns the configured identity, not a compiled-in one")
	void returnsConfiguredIdentity() throws Exception {
		MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(controller("Coursework Checker", "Example University", "grader.example.org", 2299))
			.build();

		mockMvc.perform(get("/api/v1/meta"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Coursework Checker"))
			.andExpect(jsonPath("$.organizationName").value("Example University"))
			.andExpect(jsonPath("$.sshHost").value("grader.example.org"))
			.andExpect(jsonPath("$.sshPort").value(2299));
	}

	@Test
	@DisplayName("a different configuration produces a different response")
	void tracksConfiguration() throws Exception {
		// The same controller under different settings must answer differently. This is
		// what proves the value is read from configuration rather than baked in.
		MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(controller("Second Instance", "Another Operator", "other.example.org", 22))
			.build();

		mockMvc.perform(get("/api/v1/meta"))
			.andExpect(jsonPath("$.name").value("Second Instance"))
			.andExpect(jsonPath("$.organizationName").value("Another Operator"));
	}

	@Test
	@DisplayName("exposes no personal data")
	void exposesNothingPersonal() throws Exception {
		// Served without authentication, so it must contain nothing about any individual.
		String body = MockMvcBuilders
			.standaloneSetup(controller("GitGrader", "Example Organization", "localhost", 2222))
			.build()
			.perform(get("/api/v1/meta"))
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(body).doesNotContainIgnoringCase("student");
		assertThat(body).doesNotContainIgnoringCase("password");
		assertThat(body).doesNotContainIgnoringCase("token");
	}

	@Test
	@DisplayName("reports a version even when no build information is present")
	void toleratesMissingBuildInformation() throws Exception {
		// BuildProperties only exists when the build-info goal ran. A developer running
		// from an IDE has none, and the endpoint must still answer.
		MockMvcBuilders.standaloneSetup(controller("GitGrader", "Example Organization", "localhost", 2222))
			.build()
			.perform(get("/api/v1/meta"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.version").value("dev"));
	}

	private static MetaController controller(String name, String organization, String sshHost, int sshPort) {
		AppProperties app = new AppProperties(name, URI.create("https://" + sshHost), "support@example.org",
				organization, URI.create("https://docs.example.org"), ZoneId.of("UTC"), "/data",
				new AppProperties.Registration(true, false, 5),
				new AppProperties.ResultTokens(256, Duration.ofDays(180), 8));
		GitProperties git = new GitProperties(true, sshHost, sshPort, "0.0.0.0", sshPort, "git", "/data/hostkey.ser",
				"/data/repositories", DataSize.ofMegabytes(50), DataSize.ofMegabytes(10), 2000, Set.of("ssh-ed25519"),
				true, Duration.ofMinutes(10));
		return new MetaController(app, git, null);
	}

}
