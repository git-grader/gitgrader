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

@SpringJUnitConfig(classes = RuntimeControllerTest.MethodSecurity.class)
class RuntimeControllerTest {

	@Autowired
	private RuntimeController controller;

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
				  "enabled":true
				}
				""";

		mockMvc.perform(post("/api/v1/runtimes").contentType("application/json").content(body))
			.andExpect(status().isForbidden());
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
