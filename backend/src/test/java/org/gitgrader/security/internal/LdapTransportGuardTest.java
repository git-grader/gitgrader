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
	@DisplayName("refuses plain LDAP wherever it is enabled, not only under a profile named production")
	void refusesPlaintextByDefault() {
		// The default URL is ldap://, so enabling LDAP and changing nothing else sent the
		// instructor password and the manager bind credentials in the clear. Keying the
		// refusal to the profile name let a jar started with no profile, or with one
		// called "prod", through the same hole.
		assertThatThrownBy(() -> guard("ldap://directory:389", false).afterPropertiesSet())
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("ldaps://");
	}

	@Test
	@DisplayName("accepts ldaps://")
	void acceptsTls() {
		assertThatCode(() -> guard("ldaps://directory:636", false).afterPropertiesSet()).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("lets the development profile use a directory without TLS, because the demo ships one")
	void allowsPlaintextUnderDevelopment() {
		assertThatCode(() -> guard("ldap://localhost:389", true).afterPropertiesSet()).doesNotThrowAnyException();
	}

	private static LdapSecurityConfig.LdapTransportGuard guard(String url, boolean development) {
		SecurityProperties.Ldap ldap = new SecurityProperties.Ldap(true, url, "dc=example,dc=org", "", "", "ou=people",
				"(uid={0})", "ou=groups", "(member={0})", "instructors", "admins", "follow");
		SecurityProperties properties = new SecurityProperties(ldap, null, null, null, "csp", "rcsp");
		Environment environment = mock(Environment.class);
		when(environment.acceptsProfiles(any(Profiles.class))).thenReturn(development);
		return new LdapSecurityConfig.LdapTransportGuard(properties, environment);
	}

}
