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
