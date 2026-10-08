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

package org.gitgrader.configuration;

import java.net.URI;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * Creates RFC 9457 problem documents carrying a machine-readable {@code type}.
 *
 * <p>
 * {@code docs/api.md} tells clients to branch on the problem {@code type} instead of a
 * localized {@code detail}, and a {@link ProblemDetail} built on its own answers with
 * {@code about:blank} for every failure. Every handler that renders a problem document
 * goes through this factory, so a client can tell a rejected SSH key from a validation
 * failure without parsing prose.
 *
 * <p>
 * The type is a relative URI reference on purpose. RFC 9457 4.2 resolves it against the
 * request the problem was answered on, so it names the failure kind stably without
 * embedding this project's name or an operator's origin in the response - the same
 * neutrality the PMD and Checkstyle rules require everywhere else.
 *
 * <p>
 * Declared in the shared configuration module: the types are part of the deployment's
 * public contract, and every web module may read configuration without an explicit
 * dependency.
 */
public final class ProblemTypes {

	private ProblemTypes() {
	}

	/**
	 * Creates a problem document typed with the given slug.
	 * @param status HTTP status the problem is answered with
	 * @param type stable slug identifying the failure kind, for example
	 * {@code validation-failed}
	 * @param detail the one human readable sentence the caller may be shown
	 * @return a typed problem document
	 */
	public static ProblemDetail of(HttpStatus status, String type, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		problem.setType(URI.create("/errors/" + type));
		return problem;
	}

}
