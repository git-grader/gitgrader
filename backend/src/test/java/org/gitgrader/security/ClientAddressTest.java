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

package org.gitgrader.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that a caller cannot choose the address it is rate limited by.
 *
 * <p>
 * Reading {@code X-Forwarded-For} straight off the request is the difference between a
 * limit and a suggestion: the header is client-supplied, so a new value on every attempt
 * is a new bucket on every attempt. Whether the header may be trusted is the operator's
 * decision, made once through {@code server.forward-headers-strategy}, and honouring it
 * means asking the request for its remote address rather than parsing the header here.
 */
class ClientAddressTest {

	@Test
	@DisplayName("ignores a forwarded-for header the caller supplied itself")
	void ignoresForgedForwardedForHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.7");
		request.addHeader("X-Forwarded-For", "198.51.100.23");

		assertThat(ClientAddress.of(request)).isEqualTo("203.0.113.7");
	}

	@Test
	@DisplayName("gives every attempt from one caller the same address however the header changes")
	void givesTheSameAddressWhateverTheHeaderSays() {
		// The attack this closes: rotating the header once per request, so each attempt
		// lands in a bucket of its own and no limit is ever reached.
		assertThat(addressWithForwardedFor("198.51.100.1")).isEqualTo(addressWithForwardedFor("198.51.100.2"))
			.isEqualTo(addressWithForwardedFor(null));
	}

	@Test
	@DisplayName("reports the remote address when no proxy header is present")
	void reportsTheRemoteAddressWithoutAHeader() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("192.0.2.10");

		assertThat(ClientAddress.of(request)).isEqualTo("192.0.2.10");
	}

	private static String addressWithForwardedFor(String forwardedFor) {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr("203.0.113.7");
		if (forwardedFor != null) {
			request.addHeader("X-Forwarded-For", forwardedFor);
		}
		return ClientAddress.of(request);
	}

}
