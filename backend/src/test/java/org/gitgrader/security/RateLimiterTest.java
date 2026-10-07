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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RateLimiter}, the bucket that stands between a public endpoint and a
 * script that keeps trying it.
 *
 * <p>
 * Buckets are keyed on a hash of the client address rather than on the address, so a
 * registry of tracked callers holds no network identifiers. The assertion pins the
 * arithmetic that decides who is refused: the call after the configured capacity fails.
 */
class RateLimiterTest {

	@Test
	@DisplayName("consumes tokens and refuses once the bucket is empty")
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
