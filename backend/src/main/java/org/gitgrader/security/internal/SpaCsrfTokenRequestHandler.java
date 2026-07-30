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

import java.util.function.Supplier;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;

/**
 * Resolves CSRF tokens for both rendered forms and the single-page application.
 *
 * <p>
 * The default handler masks the token on every render to frustrate BREACH, which means
 * the value a client must send back is not the value stored in the cookie. That is fine
 * for a server-rendered form, which submits the masked value it was given, but the SPA
 * has only the cookie to read, so sending it back is rejected and every state-changing
 * request fails with 403.
 *
 * <p>
 * Splitting the two cases keeps the masking where it protects something and accepts the
 * plain cookie value where the client could not possibly know the masked one: a header
 * can only be set by script, and same-origin policy already stops a foreign site from
 * reading the cookie to set it.
 */
class SpaCsrfTokenRequestHandler extends CsrfTokenRequestAttributeHandler {

	private final CsrfTokenRequestHandler maskingHandler = new XorCsrfTokenRequestAttributeHandler();

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
		this.maskingHandler.handle(request, response, csrfToken);
	}

	@Override
	public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
		if (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName()))) {
			return super.resolveCsrfTokenValue(request, csrfToken);
		}
		return this.maskingHandler.resolveCsrfTokenValue(request, csrfToken);
	}

}
