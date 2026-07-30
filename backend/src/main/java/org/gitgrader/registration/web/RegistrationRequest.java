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
import jakarta.validation.constraints.Size;

import org.jspecify.annotations.Nullable;

/**
 * Payload for self-registration. Server-side validation is authoritative.
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
		@NotBlank @Size(max = 100) String lastName, @NotBlank @Size(max = 50) String studentNumber,
		@NotBlank @Email @Size(max = 255) String email, @NotBlank @Size(max = 64) String courseKey,
		@Nullable @Size(max = 64) String classKey, @NotBlank @Size(max = 4096) String publicKey) {
}
