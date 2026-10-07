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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.gitgrader.identity.StudentStatus;
import org.gitgrader.security.ClientAddress;
import org.gitgrader.registration.internal.RegistrationService;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public endpoint for student self-registration.
 */
@RestController
@RequestMapping("/api/v1/registration")
public class RegistrationController {

	private final RegistrationService registrationService;

	public RegistrationController(RegistrationService registrationService) {
		this.registrationService = registrationService;
	}

	@GetMapping("/availability")
	public AvailabilityResponse getAvailability() {
		return this.registrationService.getAvailability();
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request,
			HttpServletRequest httpRequest) {
		return this.registrationService.register(request, ClientAddress.of(httpRequest));
	}

	/**
	 * Payload for self-registration. Server-side validation is authoritative.
	 *
	 * <p>
	 * The student username and course key become path segments of the repository the
	 * student pushes to, so both are restricted to characters that cannot denote a
	 * directory other than their own. {@code GitRepositoryService.repositoryPathFor}
	 * refuses the rest regardless; constraining them here is what turns that refusal into
	 * a field-level 400 on the registration form rather than a failure while provisioning
	 * afterwards.
	 *
	 * @param firstName student's given name
	 * @param lastName student's family name
	 * @param studentUsername institutional identifier
	 * @param email contact address
	 * @param courseKey the course to join
	 * @param classKey the class within the course
	 * @param publicKey OpenSSH public key string
	 */
	public record RegistrationRequest(@NotBlank @Size(max = 100) String firstName,
			@NotBlank @Size(max = 100) String lastName,
			@NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+",
					message = "must contain only letters, digits, '.', '_' and '-'") String studentUsername,
			@NotBlank @Email @Size(max = 255) String email,
			@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+",
					message = "must contain only letters, digits, '.', '_' and '-'") String courseKey,
			@Nullable @Size(max = 64) String classKey, @NotBlank @Size(max = 4096) String publicKey) {
	}

	/**
	 * Response after successful self-registration.
	 *
	 * @param studentId the generated internal id
	 * @param studentUsername institutional id
	 * @param fullName display name
	 * @param status current lifecycle status (usually SELF_REGISTERED)
	 * @param keyFingerprint fingerprint of the accepted SSH key
	 */
	public record RegistrationResponse(UUID studentId, String studentUsername, String fullName, StudentStatus status,
			String keyFingerprint) {
	}

	/**
	 * Indicates whether registration is open and which courses can be joined.
	 */
	public record AvailabilityResponse(boolean open, @Nullable Instant opensAt, @Nullable Instant closesAt,
			List<CourseOffering> courses) {

		/**
		 * A course currently accepting registrations.
		 *
		 * @param courseKey stable key used in clone URLs
		 * @param name display name
		 * @param classes the classes a student may pick
		 */
		public record CourseOffering(String courseKey, String name, List<ClassOffering> classes) {
		}

		/**
		 * A class within a course.
		 *
		 * @param classKey stable key
		 * @param name display name
		 */
		public record ClassOffering(String classKey, String name) {
		}

	}

}
