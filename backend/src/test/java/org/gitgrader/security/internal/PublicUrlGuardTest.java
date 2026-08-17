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

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

import org.gitgrader.configuration.AppProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * A result link is a bearer token, so it must not be published over plain HTTP.
 */
class PublicUrlGuardTest {

	@Test
	@DisplayName("refuses an http:// public URL in production")
	void refusesPlainHttpInProduction() {
		// The link is printed into the student's terminal after every push, so an
		// operator
		// who leaves the default in place hands out one token per submission in the
		// clear.
		assertThatThrownBy(() -> guard("http://grader.example.org", true).afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("https://");
	}

	@Test
	@DisplayName("accepts https:// in production")
	void acceptsHttps() {
		assertThatCode(() -> guard("https://grader.example.org", true).afterPropertiesSet()).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("leaves loopback alone, because that is the demo the documentation describes")
	void allowsLoopback() {
		assertThatCode(() -> guard("http://localhost:8080", true).afterPropertiesSet()).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("treats the whole loopback range as loopback, not just the spellings we thought of")
	void allowsAnyLoopbackLiteral() {
		assertThatCode(() -> guard("http://127.0.0.1:8080", true).afterPropertiesSet()).doesNotThrowAnyException();
		assertThatCode(() -> guard("http://127.9.9.9:8080", true).afterPropertiesSet()).doesNotThrowAnyException();
		assertThatCode(() -> guard("http://[::1]:8080", true).afterPropertiesSet()).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("a routable literal is not loopback")
	void rejectsRoutableLiteral() {
		assertThatThrownBy(() -> guard("http://10.0.0.5:8080", true).afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("does not constrain other profiles")
	void ignoresOtherProfiles() {
		assertThatCode(() -> guard("http://grader.example.org", false).afterPropertiesSet()).doesNotThrowAnyException();
	}

	private static PublicUrlConfig.PublicUrlGuard guard(String url, boolean production) {
		AppProperties properties = new AppProperties("GitGrader", URI.create(url), "support@example.org",
				"Example Organization", URI.create("https://example.org/docs"), ZoneId.of("UTC"), "/data",
				new AppProperties.Registration(true, false, 5),
				new AppProperties.ResultTokens(256, Duration.ofDays(180), 8));
		Environment environment = mock(Environment.class);
		when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(production);
		return new PublicUrlConfig.PublicUrlGuard(properties, environment);
	}

}
