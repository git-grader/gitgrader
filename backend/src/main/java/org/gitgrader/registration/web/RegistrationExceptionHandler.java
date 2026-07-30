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

import java.util.List;

import org.gitgrader.registration.internal.DuplicateRegistrationException;
import org.gitgrader.registration.internal.RateLimitExceededException;
import org.gitgrader.registration.internal.RegistrationClosedException;
import org.gitgrader.sshkeys.SshKeyRejectedException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Translates registration exceptions to RFC 9457 Problem Details.
 */
@RestControllerAdvice(assignableTypes = RegistrationController.class)
public class RegistrationExceptionHandler {

	/**
	 * Renders a Bean Validation failure as a problem document with per-field errors.
	 *
	 * <p>
	 * The {@code errors} array is what lets the registration form attach a message to the
	 * input that caused it, so the field name is carried through deliberately.
	 * @param ex the validation failure raised by Spring
	 * @return an RFC 9457 problem document
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidationException(MethodArgumentNotValidException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");

		List<ValidationError> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((error) -> new ValidationError(error.getField(), error.getDefaultMessage()))
			.toList();

		problem.setProperty("errors", errors);
		return problem;
	}

	@ExceptionHandler(SshKeyRejectedException.class)
	public ProblemDetail handleSshKeyRejected(SshKeyRejectedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.publicMessage());
	}

	@ExceptionHandler(DuplicateRegistrationException.class)
	public ProblemDetail handleDuplicateRegistration(DuplicateRegistrationException ex) {
		// Generic conflict message to avoid enumeration oracle
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"A registration with these details already exists. Please contact support if you need assistance.");
	}

	@ExceptionHandler(RateLimitExceededException.class)
	public ProblemDetail handleRateLimitExceeded(RateLimitExceededException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS,
				"Too many registration attempts. Please try again later.");
	}

	@ExceptionHandler(RegistrationClosedException.class)
	public ProblemDetail handleRegistrationClosed(RegistrationClosedException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, "Registration is currently closed.");
	}

	/**
	 * One field-level validation failure.
	 *
	 * @param field the request field that failed validation
	 * @param message the human readable reason
	 */
	public record ValidationError(String field, String message) {
	}

}
