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

import java.util.List;

import org.gitgrader.configuration.SecurityProperties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The LDAP bind credentials are configuration, so anything that prints that configuration
 * prints them with it: a startup line, a support bundle, an exception message.
 *
 * <p>
 * The subject here is the properties record rather than a logging class, because that is
 * what actually carries the secret. {@link SecurityProperties.Ldap} is a record, and the
 * {@code toString} a record generates prints every component — including the bind
 * password, in cleartext. The test populates the record with a real password and pins the
 * value as absent from the rendered form, which is the assertion that would have caught
 * it.
 *
 * <p>
 * An earlier version of this test asserted that a field *name* was absent from a
 * default-constructed {@link LdapSecurityConfig}, which had no {@code toString} of its
 * own and so rendered as an object identity string. It passed whatever the credentials
 * were.
 */
class LdapCredentialsLogTest {

	private static final String PASSWORD = "correct-horse-battery-staple";

	private static SecurityProperties.Ldap ldapWithPassword() {
		return new SecurityProperties.Ldap(true, "ldaps://directory.example.org", "dc=example,dc=org", "cn=manager",
				PASSWORD, "ou=people", "(uid={0})", "ou=groups", "(member={0})", "gitgrader-instructors",
				"gitgrader-admins", "follow");
	}

	@Test
	@DisplayName("does not render the LDAP bind password when the settings are printed")
	void managerPasswordNotRendered() {
		String rendered = ldapWithPassword().toString();

		assertThat(rendered).doesNotContain(PASSWORD);
	}

	@Test
	@DisplayName("still reports the non-secret settings so a misconfiguration is visible")
	void keepsTheRestOfTheSettings() {
		String rendered = ldapWithPassword().toString();

		assertThat(rendered).contains("ldaps://directory.example.org").contains("cn=manager").contains("<set>");
	}

	@Test
	@DisplayName("reports an absent bind password as unset rather than as an empty string")
	void reportsUnsetPassword() {
		SecurityProperties.Ldap anonymous = new SecurityProperties.Ldap(true, "ldaps://directory.example.org", "dc=x",
				"", "", "ou=people", "(uid={0})", "ou=groups", "(member={0})", "gitgrader-instructors",
				"gitgrader-admins", "follow");

		assertThat(anonymous.toString()).contains("<unset>");
	}

	@Test
	@DisplayName("does not render a local development account's password when printed")
	void localAccountPasswordNotRendered() {
		SecurityProperties.LocalAccounts.Account account = new SecurityProperties.LocalAccounts.Account("demo",
				PASSWORD, "Demo Instructor", List.of("INSTRUCTOR"));

		assertThat(account.toString()).doesNotContain(PASSWORD).contains("demo").contains("INSTRUCTOR");
	}

}
