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

package org.gitgrader.templates.web;

import org.gitgrader.templates.TemplateContentRejectedException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps invalid coursework archives to safe problem documents. */
@RestControllerAdvice(basePackageClasses = { TemplateController.class, TestSuiteController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ArchiveUploadExceptionHandler {

	/**
	 * Renders an invalid archive without exposing server paths or exception details.
	 * @param exception rejected archive
	 * @return a 400 problem document
	 */
	@ExceptionHandler(ArchiveUploadException.class)
	public ProblemDetail handleInvalidArchive(ArchiveUploadException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Invalid ZIP upload");
		return problem;
	}

	/**
	 * Renders forbidden student-visible template content as a client rejection.
	 * @param exception content guard rejection
	 * @return a 400 problem document
	 */
	@ExceptionHandler(TemplateContentRejectedException.class)
	public ProblemDetail handleRejectedTemplateContent(TemplateContentRejectedException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Template content rejected");
		return problem;
	}

}
