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

import org.gitgrader.configuration.SecurityProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Provides in-memory development accounts.
 */
@Configuration(proxyBeanMethods = false)
public class LocalAccountsConfig {

	@Bean
	public LocalAccountsGuard localAccountsGuard(SecurityProperties properties, Environment environment) {
		return new LocalAccountsGuard(properties, environment);
	}

	@Bean
	public UserDetailsService localUserDetailsService(SecurityProperties properties) {
		if (!properties.localAccounts().enabled()) {
			return new InMemoryUserDetailsManager(); // Empty
		}

		UserDetails[] users = properties.localAccounts()
			.accounts()
			.stream()
			.map((account) -> User.withUsername(account.username())
				.password("{noop}" + account.password()) // Insecure but development only
				.roles(account.roles().toArray(new String[0]))
				.build())
			.toArray(UserDetails[]::new);

		return new InMemoryUserDetailsManager(users);
	}

	/**
	 * Guard bean that prevents starting in production with local accounts enabled.
	 */
	public static class LocalAccountsGuard implements InitializingBean {

		private final SecurityProperties properties;

		private final Environment environment;

		public LocalAccountsGuard(SecurityProperties properties, Environment environment) {
			this.properties = properties;
			this.environment = environment;
		}

		@Override
		public void afterPropertiesSet() {
			if (this.properties.localAccounts().enabled()
					&& this.environment.acceptsProfiles(Profiles.of("production"))) {
				throw new IllegalStateException("Local accounts must not be enabled under the production profile");
			}
		}

	}

}
