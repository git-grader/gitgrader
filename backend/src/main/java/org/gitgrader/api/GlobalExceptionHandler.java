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

import java.util.List;

import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps exceptions to RFC 9457 problem documents.
 *
 * <p>
 * Two rules shape every handler here.
 *
 * <p>
 * <strong>An expected failure is not a defect.</strong> A validation error or a missing
 * record is logged at debug and answered with a specific status; only a genuinely
 * unexpected exception is logged at error. Otherwise a scripted client hammering a bad
 * request would fill the log and hide the failures that matter.
 *
 * <p>
 * <strong>An unexpected failure reveals nothing.</strong> The stack trace and message of
 * an internal error stay in the log; the caller gets a generic 500. Exception text
 * routinely contains table names, file paths and query fragments, none of which an
 * unauthenticated caller should ever be able to elicit.
 */
@RestControllerAdvice(basePackages = { "org.gitgrader.api", "org.gitgrader.identity.web", "org.gitgrader.courses.web",
		"org.gitgrader.assignments.web", "org.gitgrader.submissions.web", "org.gitgrader.runtimes.web",
		"org.gitgrader.audit.web", "org.gitgrader.reports", "org.gitgrader.templates.web" })
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/**
	 * Renders a Bean Validation failure with per-field detail.
	 * @param ex the validation failure raised by Spring
	 * @return a problem document carrying an {@code errors} array
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
				"The request could not be accepted.");
		problem.setTitle("Validation failed");

		List<FieldError> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map((error) -> new FieldError(error.getField(), error.getDefaultMessage()))
			.toList();
		problem.setProperty("errors", errors);
		logger.debug("Rejected an invalid request", ex);
		return problem;
	}

	/**
	 * Renders a reference to something that does not exist.
	 *
	 * <p>
	 * {@link EntityNotFoundException} is handled here rather than falling through to the
	 * catch-all. The services raise it for exactly the case this method describes, and
	 * without it every one of them answered a missing runtime, template, course, student
	 * or extension with a 500 and an ERROR in the log - reporting a caller's typo as a
	 * fault of the server, and burying real incidents among them.
	 * @param ex the failure
	 * @return a 404 problem document
	 */
	@ExceptionHandler({ IllegalArgumentException.class, EntityNotFoundException.class })
	public ProblemDetail handleNotFound(RuntimeException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,
				"The requested resource does not exist.");
		problem.setTitle("Not found");
		logger.debug("Request referenced a missing resource", ex);
		return problem;
	}

	/**
	 * Renders an authorization refusal.
	 *
	 * <p>
	 * Deliberately says nothing about whether the resource exists. Distinguishing
	 * "forbidden" from "not found" would let a signed-in instructor enumerate another
	 * course's data by observing which status comes back.
	 * @param ex the refusal
	 * @return a 403 problem document
	 */
	@ExceptionHandler(AccessDeniedException.class)
	public ProblemDetail handleAccessDenied(AccessDeniedException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,
				"You do not have access to this resource.");
		problem.setTitle("Forbidden");
		logger.debug("Denied a request on authorization grounds", ex);
		return problem;
	}

	/**
	 * Renders an operation attempted against an object in the wrong state.
	 * @param ex the failure
	 * @return a 409 problem document
	 */
	@ExceptionHandler(IllegalStateException.class)
	public ProblemDetail handleConflict(IllegalStateException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"That operation is not possible in the current state.");
		problem.setTitle("Conflict");
		logger.warn("Rejected an operation because of the object's state", ex);
		return problem;
	}

	/**
	 * Renders a write refused by a database constraint, most often a reused key.
	 * @param ex the failure
	 * @return a 409 problem document
	 */
	@ExceptionHandler(DataIntegrityViolationException.class)
	public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
				"That conflicts with something that already exists. A key may already be in use.");
		problem.setTitle("Conflict");
		// The constraint name would name tables and columns, so it stays in the log.
		logger.warn("Rejected a write that violated a database constraint", ex);
		return problem;
	}

	/**
	 * Renders anything unforeseen without disclosing its detail.
	 * @param ex the failure
	 * @return a generic 500 problem document
	 */
	@ExceptionHandler(Exception.class)
	public ProblemDetail handleUnexpected(Exception ex) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
				"Something went wrong on our side.");
		problem.setTitle("Internal error");
		// The detail stays here, in the log, where an operator can correlate it. It is
		// never echoed to the caller: exception text leaks table names and file paths.
		logger.error("Unhandled exception while serving a request", ex);
		return problem;
	}

	/**
	 * One field-level validation failure.
	 *
	 * @param field the request field that failed
	 * @param message the human readable reason
	 */
	public record FieldError(String field, @org.jspecify.annotations.Nullable String message) {
	}

}
