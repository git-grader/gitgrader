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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LdapTransportGuardTest {

	@Test
	@DisplayName("refuses to start a production instance that binds over plain LDAP")
	void refusesPlaintextInProduction() {
		// The default URL is ldap://, so enabling LDAP and changing nothing else sent the
		// instructor password and the manager bind credentials in the clear.
		assertThatThrownBy(() -> guard("ldap://directory:389", true).afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ldaps://");
	}

	@Test
	@DisplayName("accepts ldaps:// in production")
	void acceptsTlsInProduction() {
		assertThatCode(() -> guard("ldaps://directory:636", true).afterPropertiesSet()).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("leaves other profiles alone, so a development directory still works")
	void allowsPlaintextElsewhere() {
		assertThatCode(() -> guard("ldap://localhost:389", false).afterPropertiesSet()).doesNotThrowAnyException();
	}

	private static LdapSecurityConfig.LdapTransportGuard guard(String url, boolean production) {
		SecurityProperties.Ldap ldap = new SecurityProperties.Ldap(true, url, "dc=example,dc=org", "", "", "ou=people",
				"(uid={0})", "ou=groups", "(member={0})", "instructors", "admins", "follow");
		SecurityProperties properties = new SecurityProperties(ldap, null, null, null, "csp", "rcsp");
		Environment environment = mock(Environment.class);
		when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(production);
		return new LdapSecurityConfig.LdapTransportGuard(properties, environment);
	}

}
