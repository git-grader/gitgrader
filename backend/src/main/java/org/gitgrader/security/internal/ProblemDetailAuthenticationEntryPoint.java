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
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * Answers an unauthenticated API call with 401 and a problem document.
 *
 * <p>
 * Spring Security's default for a form-login application is to redirect to the sign-in
 * page. That is right for a browser navigation and wrong for an API call: the SPA uses
 * {@code fetch()}, which follows a 302 transparently, so the client would receive the
 * login page's HTML with status 200 and try to parse it as JSON. The user sees a parse
 * error instead of "your session expired".
 *
 * <p>
 * The body is written by hand rather than through the configured {@code ObjectMapper}.
 * This runs inside the security filter chain, before any message converter is available,
 * and the payload is three fixed fields - introducing a serializer dependency here would
 * add a failure mode to the one code path that has to work when things are already going
 * wrong.
 */
class ProblemDetailAuthenticationEntryPoint implements AuthenticationEntryPoint {

	/**
	 * A fixed RFC 9457 document; it carries no request-specific data by design.
	 *
	 * <p>
	 * Saying only "authentication required" keeps the response from confirming whether
	 * the requested resource exists to a caller who has not signed in.
	 */
	private static final String BODY = """
			{"type":"about:blank",\
			"title":"Unauthorized",\
			"status":401,\
			"detail":"Authentication is required to access this resource."}""";

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		response.setStatus(HttpStatus.UNAUTHORIZED.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		response.getWriter().write(BODY);
	}

}
