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

package org.gitgrader.runtimes.web;

import org.junit.jupiter.api.BeforeEach;
import java.time.Instant;
import java.util.UUID;
import org.gitgrader.runtimes.ReportFormat;
import org.gitgrader.runtimes.RuntimeView;
import org.jspecify.annotations.Nullable;
import org.gitgrader.api.GlobalExceptionHandler;
import org.gitgrader.runtimes.RuntimeAdministration;
import org.gitgrader.runtimes.RuntimeCatalog;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.clearInvocations;

@SpringJUnitConfig(classes = RuntimeControllerTest.MethodSecurity.class)
class RuntimeControllerTest {

	@Autowired
	private RuntimeController controller;

	@Autowired
	private RuntimeAdministration administration;

	@BeforeEach
	void forgetEarlierCalls() {
		// The administration mock is a shared bean, so without this one test's create
		// call is counted against the next test's verification.
		clearInvocations(this.administration);
	}

	@Test
	@WithMockUser(roles = "INSTRUCTOR")
	void instructorCannotCreateRuntime() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

		String body = """
				{
				  "runtimeKey":"java",
				  "displayName":"Java",
				  "image":"example/java",
				  "tag":"25",
				  "imageDigest":"sha256:abc",
				  "testCommand":"mvn test",
				  "reportFormat":"JUNIT_XML",
				  "enabled":true,
				  "shimKind":"legacy"
				}
				""";

		mockMvc.perform(post("/api/v1/runtimes").contentType("application/json").content(body))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void refusesARuntimeThatDoesNotDeclareItsGradingTopology() throws Exception {
		// A blank topology is not a defaultable detail: it decides whether the submission
		// and the hidden suite share one sandbox, and the two return different marks for
		// the same work. So an undeclared topology is refused at the boundary rather than
		// defaulted.
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();

		mockMvc.perform(post("/api/v1/runtimes").contentType("application/json").content(runtimeBody(null)))
			.andExpect(status().isBadRequest());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void acceptsTheExplicitSingleSandboxOptOut() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
		when(this.administration.create(any())).thenReturn(view("legacy"));

		mockMvc.perform(post("/api/v1/runtimes").contentType("application/json").content(runtimeBody("legacy")))
			.andExpect(status().isCreated());
		verify(this.administration).create(any());
	}

	@Test
	@WithMockUser(roles = "ADMIN")
	void acceptsANamedShimTopology() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(this.controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.build();
		when(this.administration.create(any())).thenReturn(view("node"));

		mockMvc.perform(post("/api/v1/runtimes").contentType("application/json").content(runtimeBody("node")))
			.andExpect(status().isCreated());
	}

	private static String runtimeBody(@Nullable String shimKind) {
		String topology = shimKind == null ? "" : "\"shimKind\":\"" + shimKind + "\",";
		return """
				{
				  "runtimeKey":"java",
				  "displayName":"Java",
				  "image":"example/java",
				  "tag":"25",
				  "imageDigest":"sha256:%s",
				  "testCommand":"mvn test",
				  "reportFormat":"JUNIT_XML",
				  "enabled":true,
				  %s
				  "shimCommand":null
				}
				""".formatted("a".repeat(64), topology);
	}

	private static RuntimeView view(@Nullable String shimKind) {
		return new RuntimeView(UUID.randomUUID(), "java", "Java", "example/java", "25", "sha256:" + "a".repeat(64),
				null, "mvn test", ReportFormat.JUNIT_XML, true, Instant.parse("2026-03-01T10:15:30Z"),
				Instant.parse("2026-03-01T10:15:30Z"), shimKind, null);
	}

	@EnableMethodSecurity
	static class MethodSecurity {

		@Bean
		RuntimeCatalog runtimeCatalog() {
			return mock(RuntimeCatalog.class);
		}

		@Bean
		RuntimeAdministration runtimeAdministration() {
			return mock(RuntimeAdministration.class);
		}

		@Bean
		RuntimeController runtimeController(RuntimeCatalog catalog, RuntimeAdministration administration) {
			return new RuntimeController(catalog, administration);
		}

	}

}
