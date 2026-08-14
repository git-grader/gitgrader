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

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * Payload for self-registration. Server-side validation is authoritative.
 *
 * <p>
 * The student number and course key become path segments of the repository the student
 * pushes to, so both are restricted to characters that cannot denote a directory other
 * than their own. {@code GitRepositoryService.repositoryPathFor} refuses the rest
 * regardless; constraining them here is what turns that refusal into a field-level 400 on
 * the registration form rather than a failure while provisioning afterwards.
 *
 * @param firstName student's given name
 * @param lastName student's family name
 * @param studentNumber institutional identifier
 * @param email contact address
 * @param courseKey the course to join
 * @param classKey the class within the course
 * @param publicKey OpenSSH public key string
 */
public record RegistrationRequest(@NotBlank @Size(max = 100) String firstName,
		@NotBlank @Size(max = 100) String lastName,
		@NotBlank @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9._-]+",
				message = "must contain only letters, digits, '.', '_' and '-'") String studentNumber,
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Size(max = 64) @Pattern(regexp = "[A-Za-z0-9._-]+",
				message = "must contain only letters, digits, '.', '_' and '-'") String courseKey,
		@Nullable @Size(max = 64) String classKey, @NotBlank @Size(max = 4096) String publicKey) {
}
