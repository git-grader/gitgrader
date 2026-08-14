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

import org.gitgrader.audit.AuditProperties;
import org.gitgrader.audit.ClientAddressHasher;
import org.gitgrader.configuration.SecurityProperties;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RateLimiterTest {

	@Test
	void consumesTokensAndRefusesWhenEmpty() {
		ClientAddressHasher hasher = new ClientAddressHasher(
				new AuditProperties("secret-key", java.time.Duration.ofDays(1)));
		SecurityProperties.RateLimits limits = new SecurityProperties.RateLimits(2, 200, 60, 10, 30, 20, 60,
				Duration.ofMinutes(15));
		SecurityProperties props = new SecurityProperties(null, null, limits, null, "csp", "rcsp");

		RateLimiter limiter = new RateLimiter(hasher, props);

		String ip = "1.2.3.4";

		assertThat(limiter.tryConsumeRegistrationPerIp(ip)).isTrue();
		assertThat(limiter.tryConsumeRegistrationPerIp(ip)).isTrue();
		assertThat(limiter.tryConsumeRegistrationPerIp(ip)).isFalse();
	}

}
