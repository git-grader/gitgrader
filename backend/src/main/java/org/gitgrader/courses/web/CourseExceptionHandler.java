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

package org.gitgrader.courses.web;

import org.gitgrader.courses.CourseIdentityMismatchException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps invalid course and class updates to safe problem documents. */
@RestControllerAdvice(basePackageClasses = CourseController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CourseExceptionHandler {

	/**
	 * Renders an attempted course or class identity change as a client error.
	 * @param exception identity mismatch
	 * @return a 400 problem document
	 */
	@ExceptionHandler(CourseIdentityMismatchException.class)
	public ProblemDetail handleIdentityMismatch(CourseIdentityMismatchException exception) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, exception.getMessage());
		problem.setTitle("Course identity cannot change");
		return problem;
	}

}
