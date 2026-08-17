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

package org.gitgrader.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.PropertyPlaceholderHelper;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * The production profile must not inherit the standalone database credentials.
 */
class ProductionDatasourceCredentialsTest {

	private static final PropertyPlaceholderHelper RESOLVER = new PropertyPlaceholderHelper("${", "}", ":", null,
			false);

	@Test
	@DisplayName("refuses to resolve the database credentials from a default")
	void hasNoFallbackCredentials() throws IOException {
		// The base profile falls back to gitgrader/gitgrader so a local run needs no
		// configuration. Inheriting that in production means reaching a real database
		// with credentials anyone can guess, so the placeholder must have no default and
		// startup must fail instead.
		StandardEnvironment environment = new StandardEnvironment();
		for (String property : List.of("spring.datasource.username", "spring.datasource.password")) {
			String declared = String.valueOf(productionProperties().getProperty(property));

			assertThat(declared).doesNotContain(":");
			assertThatExceptionOfType(IllegalArgumentException.class)
				.isThrownBy(() -> RESOLVER.replacePlaceholders(declared, environment::getProperty));
		}
	}

	private static PropertySource<?> productionProperties() throws IOException {
		return new YamlPropertySourceLoader()
			.load("application-production", new ClassPathResource("application-production.yaml"))
			.getFirst();
	}

}
