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

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.gitgrader.security.RateLimiter;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bounds sign-in attempts from one source.
 *
 * <p>
 * Sits in front of the authentication filter rather than reacting to its outcome. A
 * password check is deliberately expensive and a directory bind reaches a second system,
 * so the useful place to refuse a flood is before either happens.
 *
 * <p>
 * Every attempt is counted, not only the failures. Counting failures alone would let an
 * attacker who guesses correctly on the first try of each burst continue indefinitely,
 * and would make the limit depend on the answer rather than on the volume.
 */
class LoginRateLimitFilter extends OncePerRequestFilter {

	private final RateLimiter rateLimiter;

	private final String loginProcessingUrl;

	LoginRateLimitFilter(RateLimiter rateLimiter, String loginProcessingUrl) {
		this.rateLimiter = rateLimiter;
		this.loginProcessingUrl = loginProcessingUrl;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		return !("POST".equalsIgnoreCase(request.getMethod())
				&& this.loginProcessingUrl.equals(request.getServletPath()));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		if (!this.rateLimiter.tryConsumeLoginPerIp(clientAddress(request))) {
			response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
			return;
		}
		filterChain.doFilter(request, response);
	}

	/**
	 * Resolves the address an attempt is counted against.
	 *
	 * <p>
	 * {@code X-Forwarded-For} is only meaningful when a trusted proxy sets it, which is
	 * the same condition {@code server.forward-headers-strategy} governs for the rest of
	 * the application. The limiter hashes whatever this returns before using it as a key.
	 * @param request the sign-in request
	 * @return the address to count against
	 */
	private static String clientAddress(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		if (forwarded == null || forwarded.isBlank()) {
			return request.getRemoteAddr();
		}
		return forwarded.split(",")[0].trim();
	}

}
