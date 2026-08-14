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

import java.util.Collection;
import java.util.List;

import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the mapping from directory groups to the two roles this application has.
 *
 * <p>
 * Everything an instructor or an administrator is allowed to do rests on this one
 * translation, and it had no test at all: the coverage report showed it as untested and
 * was, for once, right. The cases below are the ones a directory actually presents -
 * membership of one group, of both, of a group that means nothing here, and of none.
 */
class GroupRoleMapperTest {

	private static final String INSTRUCTORS = "gitgrader-instructors";

	private static final String ADMINS = "gitgrader-admins";

	@Test
	@DisplayName("grants instructor to a member of the instructor group")
	void mapsTheInstructorGroup() {
		assertThat(rolesFor(INSTRUCTORS)).containsExactly("ROLE_INSTRUCTOR");
	}

	@Test
	@DisplayName("grants administrator to a member of the admin group")
	void mapsTheAdminGroup() {
		assertThat(rolesFor(ADMINS)).containsExactly("ROLE_ADMIN");
	}

	@Test
	@DisplayName("grants both to someone who is in both groups")
	void mapsBothGroups() {
		assertThat(rolesFor(INSTRUCTORS, ADMINS)).containsExactlyInAnyOrder("ROLE_INSTRUCTOR", "ROLE_ADMIN");
	}

	@Test
	@DisplayName("grants nothing for any other group in the directory")
	void ignoresEveryOtherGroup() {
		// An institution's directory carries hundreds of groups that have nothing to do
		// with this application. Only the two configured names may mean anything, or
		// every member of staff becomes an instructor.
		assertThat(rolesFor("domain-users", "all-staff", "vpn-users")).isEmpty();
	}

	@Test
	@DisplayName("grants nothing to a valid account that is in neither group")
	void grantsNothingWithoutAGroup() {
		// Not a refusal: they authenticate and reach the current-user endpoint and the
		// public pages, because every controller holding data demands one of the two
		// roles. Worth asserting so that it stays a deliberate state rather than
		// becoming one by accident.
		assertThat(rolesFor()).isEmpty();
	}

	@Test
	@DisplayName("matches the group name exactly as the directory spells it")
	void doesNotMatchOnCase() {
		// The populator is deliberately configured not to upper-case or prefix what it
		// reads. If that changes, this is the assertion that notices before an
		// instructor silently loses their role at the next sign-in.
		assertThat(rolesFor("GitGrader-Instructors")).isEmpty();
	}

	private static List<String> rolesFor(String... groups) {
		LdapAuthoritiesPopulator directory = (userData, username) -> List.of(groups)
			.stream()
			.map((group) -> (GrantedAuthority) new SimpleGrantedAuthority(group))
			.toList();
		Collection<? extends GrantedAuthority> roles = new GroupRoleMapper(directory, INSTRUCTORS, ADMINS)
			.getGrantedAuthorities((DirContextOperations) null, "mmuster");
		return roles.stream().map(GrantedAuthority::getAuthority).toList();
	}

}
