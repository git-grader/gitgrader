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

import java.util.Set;
import java.util.stream.Collectors;

import org.gitgrader.identity.Actor;
import org.gitgrader.identity.ActorProvider;
import org.gitgrader.identity.ActorType;
import org.gitgrader.identity.InstructorDirectory;
import org.gitgrader.identity.InstructorView;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;

/**
 * Resolves the actor from the Spring Security context.
 */
@Component
public class SecurityContextActorProvider implements ActorProvider {

	/** Prefix Spring Security puts in front of every granted role authority. */
	private static final String ROLE_PREFIX = "ROLE_";

	private final InstructorDirectory instructorDirectory;

	public SecurityContextActorProvider(InstructorDirectory instructorDirectory) {
		this.instructorDirectory = instructorDirectory;
	}

	@Override
	public Actor currentActor() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

		if (authentication == null || !authentication.isAuthenticated()
				|| "anonymousUser".equals(authentication.getPrincipal())) {
			return new Actor(ActorType.ANONYMOUS, null, "Anonymous");
		}

		Object principal = authentication.getPrincipal();
		String username;
		String displayName;

		if (principal instanceof UserDetails userDetails) {
			username = userDetails.getUsername();
			displayName = userDetails.getUsername(); // simplistic fallback
		}
		else {
			username = (principal != null) ? principal.toString() : "unknown";
			displayName = username;
		}

		Set<String> roles = authentication.getAuthorities()
			.stream()
			.map(GrantedAuthority::getAuthority)
			.filter((authority) -> authority.startsWith(ROLE_PREFIX))
			.map((authority) -> authority.substring(ROLE_PREFIX.length()))
			.collect(Collectors.toSet());

		// Upsert the instructor on login
		InstructorView instructor = this.instructorDirectory.upsertOnLogin(username, displayName, null, roles);

		if (roles.contains("ADMIN")) {
			return new Actor(ActorType.ADMIN, instructor.id().toString(), instructor.displayName());
		}

		return new Actor(ActorType.INSTRUCTOR, instructor.id().toString(), instructor.displayName());
	}

}
