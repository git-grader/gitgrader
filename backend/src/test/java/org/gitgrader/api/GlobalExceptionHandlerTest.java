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

import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

	private final MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
		.setControllerAdvice(new GlobalExceptionHandler())
		.build();

	@Test
	void aMissingEntityIsNotFoundRatherThanAServerFault() throws Exception {
		this.mockMvc.perform(get("/missing-entity"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.title").value("Not found"));
	}

	@Test
	void anArgumentTheDomainRefusedIsABadRequest() throws Exception {
		// Sharing the handler with EntityNotFoundException made an unsupported export
		// format and a path that escaped its root both answer "the requested resource
		// does
		// not exist", which is the opposite of what happened.
		this.mockMvc.perform(get("/missing-argument"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.title").value("Bad request"));
	}

	/**
	 * The detail of the failure never reaches the caller, because it carries table and
	 * column names.
	 */
	@Test
	void theReasonIsNotDisclosedToTheCaller() throws Exception {
		this.mockMvc.perform(get("/missing-entity"))
			.andExpect(jsonPath("$.detail").value("The requested resource does not exist."));
	}

	@RestController
	static final class ThrowingController {

		@GetMapping("/missing-entity")
		String missingEntity() {
			throw new EntityNotFoundException("Runtime not found: 42");
		}

		@GetMapping("/missing-argument")
		String missingArgument() {
			throw new IllegalArgumentException("Assignment not found");
		}

	}

}
