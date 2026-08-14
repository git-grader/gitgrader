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

package org.gitgrader.registration.web;

import java.util.UUID;
import java.util.List;

import org.gitgrader.identity.StudentStatus;
import org.gitgrader.registration.internal.DuplicateRegistrationException;
import org.gitgrader.registration.internal.RateLimitExceededException;
import org.gitgrader.registration.internal.RegistrationClosedException;
import org.gitgrader.registration.internal.RegistrationService;
import org.gitgrader.sshkeys.SshKeyRejectedException;
import org.gitgrader.sshkeys.SshKeyRejectionReason;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RegistrationControllerTest {

	private MockMvc mockMvc;

	private RegistrationService registrationService;

	@BeforeEach
	void setUp() {
		registrationService = Mockito.mock(RegistrationService.class);
		mockMvc = MockMvcBuilders.standaloneSetup(new RegistrationController(registrationService))
			.setControllerAdvice(new RegistrationExceptionHandler())
			.build();
	}

	@Test
	void successfulRegistrationReturns201() throws Exception {
		RegistrationResponse response = new RegistrationResponse(UUID.randomUUID(), "12345", "John Doe",
				StudentStatus.SELF_REGISTERED, "SHA256:fingerprint", List.of());
		when(registrationService.register(any(), anyString())).thenReturn(response);

		mockMvc.perform(post("/api/v1/registration").contentType(MediaType.APPLICATION_JSON).content("""
					{
						"firstName": "John",
						"lastName": "Doe",
						"studentNumber": "12345",
						"email": "john@example.com",
						"courseKey": "cs101",
						"publicKey": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI..."
					}
				"""))
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.studentId").exists())
			.andExpect(jsonPath("$.status").value("SELF_REGISTERED"));
	}

	@Test
	void privateKeySubmittedReturns400() throws Exception {
		when(registrationService.register(any(), anyString()))
			.thenThrow(new SshKeyRejectedException(SshKeyRejectionReason.PRIVATE_KEY_SUBMITTED));

		mockMvc.perform(post("/api/v1/registration").contentType(MediaType.APPLICATION_JSON).content("""
					{
						"firstName": "John",
						"lastName": "Doe",
						"studentNumber": "12345",
						"email": "john@example.com",
						"courseKey": "cs101",
						"publicKey": "-----BEGIN OPENSSH PRIVATE KEY-----"
					}
				"""))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value(SshKeyRejectionReason.PRIVATE_KEY_SUBMITTED.publicMessage()));
	}

	@Test
	void duplicateRegistrationReturns409() throws Exception {
		when(registrationService.register(any(), anyString()))
			.thenThrow(new DuplicateRegistrationException("already exists"));

		mockMvc.perform(post("/api/v1/registration").contentType(MediaType.APPLICATION_JSON).content("""
					{
						"firstName": "John",
						"lastName": "Doe",
						"studentNumber": "12345",
						"email": "john@example.com",
						"courseKey": "cs101",
						"publicKey": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI..."
					}
				""")).andExpect(status().isConflict());
	}

	@Test
	void rateLimitedReturns429() throws Exception {
		when(registrationService.register(any(), anyString())).thenThrow(new RateLimitExceededException("rate limit"));

		mockMvc.perform(post("/api/v1/registration").contentType(MediaType.APPLICATION_JSON).content("""
					{
						"firstName": "John",
						"lastName": "Doe",
						"studentNumber": "12345",
						"email": "john@example.com",
						"courseKey": "cs101",
						"publicKey": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI..."
					}
				""")).andExpect(status().isTooManyRequests());
	}

	@Test
	void registrationClosedReturns403() throws Exception {
		when(registrationService.register(any(), anyString())).thenThrow(new RegistrationClosedException("closed"));

		mockMvc.perform(post("/api/v1/registration").contentType(MediaType.APPLICATION_JSON).content("""
					{
						"firstName": "John",
						"lastName": "Doe",
						"studentNumber": "12345",
						"email": "john@example.com",
						"courseKey": "cs101",
						"publicKey": "ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAI..."
					}
				""")).andExpect(status().isForbidden());
	}

}
