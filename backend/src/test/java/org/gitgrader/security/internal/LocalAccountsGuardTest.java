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
import org.junit.jupiter.api.Test;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LocalAccountsGuardTest {

	@Test
	void throwsWhenEnabledInProduction() {
		SecurityProperties.LocalAccounts localAccounts = new SecurityProperties.LocalAccounts(true, List.of());
		SecurityProperties props = new SecurityProperties(null, localAccounts, null, null, "csp", "rcsp");

		Environment env = mock(Environment.class);
		when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true); // accepts
																			// production

		LocalAccountsConfig.LocalAccountsGuard guard = new LocalAccountsConfig.LocalAccountsGuard(props, env);

		assertThatThrownBy(guard::afterPropertiesSet).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("production profile");
	}

	@Test
	void passesWhenDisabledInProduction() {
		SecurityProperties.LocalAccounts localAccounts = new SecurityProperties.LocalAccounts(false, List.of());
		SecurityProperties props = new SecurityProperties(null, localAccounts, null, null, "csp", "rcsp");

		Environment env = mock(Environment.class);
		when(env.acceptsProfiles(any(Profiles.class))).thenReturn(true);

		LocalAccountsConfig.LocalAccountsGuard guard = new LocalAccountsConfig.LocalAccountsGuard(props, env);

		assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
	}

	@Test
	void passesWhenEnabledInDevelopment() {
		SecurityProperties.LocalAccounts localAccounts = new SecurityProperties.LocalAccounts(true, List.of());
		SecurityProperties props = new SecurityProperties(null, localAccounts, null, null, "csp", "rcsp");

		Environment env = mock(Environment.class);
		when(env.acceptsProfiles(any(Profiles.class))).thenReturn(false); // not
																			// production

		LocalAccountsConfig.LocalAccountsGuard guard = new LocalAccountsConfig.LocalAccountsGuard(props, env);

		assertThatCode(guard::afterPropertiesSet).doesNotThrowAnyException();
	}

}
