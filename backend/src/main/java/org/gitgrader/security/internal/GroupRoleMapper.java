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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ldap.core.DirContextOperations;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.ldap.userdetails.LdapAuthoritiesPopulator;

/**
 * Turns the directory groups a user belongs to into the two roles this application has.
 *
 * <p>
 * This is the whole of the authorization decision for everyone who signs in: which
 * directory group grants instructor, which grants administrator, and nothing else. Only
 * the two configured groups are recognised, so membership of anything else - including
 * every other group in an institution's directory - grants nothing.
 *
 * <p>
 * A user in neither group is not refused. They authenticate with no authorities at all,
 * which leaves them the current-user endpoint and the pages anyone can reach; every
 * controller carrying data requires one of the two roles. Refusing the sign-in instead
 * would be a behaviour change, not a fix, and the distinction is worth keeping visible.
 */
class GroupRoleMapper implements LdapAuthoritiesPopulator {

	static final String INSTRUCTOR_ROLE = "ROLE_INSTRUCTOR";

	static final String ADMIN_ROLE = "ROLE_ADMIN";

	private static final Logger logger = LoggerFactory.getLogger(GroupRoleMapper.class);

	private final LdapAuthoritiesPopulator groups;

	private final String instructorGroup;

	private final String adminGroup;

	GroupRoleMapper(LdapAuthoritiesPopulator groups, String instructorGroup, String adminGroup) {
		this.groups = groups;
		this.instructorGroup = instructorGroup;
		this.adminGroup = adminGroup;
	}

	@Override
	public Collection<? extends GrantedAuthority> getGrantedAuthorities(DirContextOperations userData,
			String username) {
		Set<GrantedAuthority> roles = new HashSet<>();
		List<String> groupNames = new ArrayList<>();
		for (GrantedAuthority group : this.groups.getGrantedAuthorities(userData, username)) {
			String name = group.getAuthority();
			groupNames.add(name);
			if (this.instructorGroup.equals(name)) {
				roles.add(new SimpleGrantedAuthority(INSTRUCTOR_ROLE));
			}
			if (this.adminGroup.equals(name)) {
				roles.add(new SimpleGrantedAuthority(ADMIN_ROLE));
			}
		}
		if (roles.isEmpty()) {
			// Otherwise this is invisible from the server. The account is valid, the
			// sign-in succeeds, and the application is simply empty of everything -
			// which is what a group name spelled differently here than in the directory
			// looks like, and there is nothing anywhere to say so.
			logger.info(
					"{} signed in with no GitGrader role: none of their {} directory group(s) matched "
							+ "the configured instructor group '{}' or admin group '{}'",
					username, groupNames.size(), this.instructorGroup, this.adminGroup);
			logger.debug("Groups returned for {}: {}", username, groupNames);
		}
		return roles;
	}

}
