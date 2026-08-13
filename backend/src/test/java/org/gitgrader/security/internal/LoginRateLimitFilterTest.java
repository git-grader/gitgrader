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

import java.time.Duration;
import java.util.Locale;

import org.gitgrader.audit.AuditProperties;
import org.gitgrader.audit.ClientAddressHasher;
import org.gitgrader.configuration.SecurityProperties;
import org.gitgrader.security.RateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that the sign-in limit counts a caller, not a header it chose.
 */
class LoginRateLimitFilterTest {

	private static final String LOGIN_URL = "/login";

	private static final int ALLOWED_PER_MINUTE = 3;

	private LoginRateLimitFilter filter;

	@BeforeEach
	void setUp() {
		ClientAddressHasher hasher = new ClientAddressHasher(new AuditProperties("secret-key", Duration.ofDays(1)));
		SecurityProperties.RateLimits limits = new SecurityProperties.RateLimits(5, 200, 60, ALLOWED_PER_MINUTE, 30, 20,
				60, Duration.ofMinutes(15));
		SecurityProperties properties = new SecurityProperties(null, null, limits, null, "csp", "rcsp");
		this.filter = new LoginRateLimitFilter(new RateLimiter(hasher, properties), LOGIN_URL);
	}

	@Test
	@DisplayName("stops brute force from one address once the allowance is spent")
	void refusesFurtherAttemptsFromTheSameAddress() throws Exception {
		for (int attempt = 0; attempt < ALLOWED_PER_MINUTE; attempt++) {
			assertThat(attemptLogin(null)).isEqualTo(HttpStatus.OK.value());
		}

		assertThat(attemptLogin(null)).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
	}

	@Test
	@DisplayName("does not hand out a fresh allowance to a caller that rewrites its forwarded-for header")
	void refusesAttemptsThatRotateTheForwardedForHeader() throws Exception {
		// Every request comes from the same peer and only the header changes. Counting
		// the header would give this caller an unlimited number of sign-in attempts.
		for (int attempt = 0; attempt < ALLOWED_PER_MINUTE; attempt++) {
			assertThat(attemptLogin("198.51.100." + attempt)).isEqualTo(HttpStatus.OK.value());
		}

		assertThat(attemptLogin("198.51.100.240")).isEqualTo(HttpStatus.TOO_MANY_REQUESTS.value());
	}

	@Test
	@DisplayName("counts genuinely different callers separately")
	void countsDistinctCallersSeparately() throws Exception {
		for (int attempt = 0; attempt < ALLOWED_PER_MINUTE; attempt++) {
			assertThat(attemptLogin(null)).isEqualTo(HttpStatus.OK.value());
		}

		MockHttpServletResponse response = new MockHttpServletResponse();
		this.filter.doFilter(loginRequest(null, "203.0.113.99"), response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
	}

	@Test
	@DisplayName("leaves anything that is not a sign-in attempt alone")
	void ignoresRequestsThatAreNotSignIn() throws Exception {
		MockHttpServletRequest request = loginRequest(null, "203.0.113.7");
		request.setServletPath("/api/v1/courses");
		request.setMethod("GET");

		for (int attempt = 0; attempt < ALLOWED_PER_MINUTE * 3; attempt++) {
			MockHttpServletResponse response = new MockHttpServletResponse();
			this.filter.doFilter(request, response, new MockFilterChain());
			assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
		}
	}

	private int attemptLogin(String forwardedFor) throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();
		this.filter.doFilter(loginRequest(forwardedFor, "203.0.113.7"), response, new MockFilterChain());
		return response.getStatus();
	}

	private static MockHttpServletRequest loginRequest(String forwardedFor, String remoteAddress) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setMethod("POST");
		request.setServletPath(LOGIN_URL);
		request.setRemoteAddr(remoteAddress);
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor.toLowerCase(Locale.ROOT));
		}
		return request;
	}

}
