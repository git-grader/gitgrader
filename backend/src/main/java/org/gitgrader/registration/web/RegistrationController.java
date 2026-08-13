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

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.gitgrader.security.ClientAddress;
import org.gitgrader.registration.internal.RegistrationService;
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

}
