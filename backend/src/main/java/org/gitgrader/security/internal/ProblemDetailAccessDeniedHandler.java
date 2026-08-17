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

package org.gitgrader.security.internal;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Answers a refused API call with 403 and a problem document.
 *
 * <p>
 * The sibling of {@link ProblemDetailAuthenticationEntryPoint}, for the other half of the
 * problem. Without it, a request that fails the CSRF check is handled by Spring
 * Security's default, which redirects to the sign-in page: an API client posting JSON
 * received a 302 to an HTML page, and the SPA's {@code fetch} followed it and tried to
 * parse the login page as its answer. The redirect also carried the session identifier in
 * the URL, which is how a session ends up in a proxy log.
 */
class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

	/**
	 * A fixed RFC 9457 document.
	 *
	 * <p>
	 * The reason is deliberately unspecific: distinguishing "your token was stale" from
	 * "you may not do this" tells a caller which of the two to work on.
	 */
	private static final String BODY = """
			{"type":"about:blank",\
			"title":"Forbidden",\
			"status":403,\
			"detail":"This request was refused. Reload the page and try again if you were signed in."}""";

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		response.setStatus(HttpStatus.FORBIDDEN.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(BODY);
	}

}
