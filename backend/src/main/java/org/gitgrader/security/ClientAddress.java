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

import jakarta.servlet.http.HttpServletRequest;

/**
 * Resolves the address a request is counted against.
 *
 * <p>
 * Deliberately does not read {@code X-Forwarded-For}. A client can put anything in that
 * header, so reading it directly means a caller chooses which rate-limit bucket it lands
 * in, and a fresh value on every request is an unlimited number of buckets. Every per-IP
 * limit in the application - sign-in attempts, result lookups, registrations - is only as
 * good as this value.
 *
 * <p>
 * Whether the header may be believed is an operator's decision, expressed once as
 * {@code server.forward-headers-strategy} and defaulting to {@code none}. When it is
 * turned on, the servlet container or Spring's own forwarded-header filter rewrites the
 * request so that {@code getRemoteAddr()} already reports the client rather than the
 * proxy. Asking for the remote address is therefore the way to honour that setting rather
 * than to bypass it.
 */
public final class ClientAddress {

	private ClientAddress() {
	}

	/**
	 * Returns the address to attribute a request to.
	 * @param request the incoming request
	 * @return the client address as the configured proxy handling reports it
	 */
	public static String of(HttpServletRequest request) {
		return request.getRemoteAddr();
	}

}
