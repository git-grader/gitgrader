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

package org.gitgrader.identity.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.gitgrader.identity.InstructorView;
import org.jspecify.annotations.Nullable;

/** Local persistence projection of an instructor from the external directory. */
@Entity
@Table(name = "instructors")
public class Instructor {

	@Id
	private UUID id;

	@Column(nullable = false)
	private String username;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column
	private @Nullable String email;

	@Column(nullable = false)
	private String roles;

	@Column(name = "first_login_at", nullable = false)
	private Instant firstLoginAt;

	@Column(name = "last_login_at", nullable = false)
	private Instant lastLoginAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Instructor() {
	}

	/**
	 * Creates the local projection on first successful login.
	 * @param username directory username
	 * @param displayName directory display name
	 * @param email directory email address
	 * @param roles application roles
	 * @param clock source of login time
	 */
	public Instructor(String username, String displayName, @Nullable String email, Set<String> roles, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.username = username;
		this.displayName = displayName;
		this.email = email;
		this.roles = rolesValue(roles);
		this.firstLoginAt = now;
		this.lastLoginAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Refreshes mutable directory attributes and the login timestamp.
	 * @param displayName directory display name
	 * @param email directory email address
	 * @param roles application roles
	 * @param clock source of login time
	 */
	public void updateOnLogin(String displayName, @Nullable String email, Set<String> roles, Clock clock) {
		Instant now = Instant.now(clock);
		this.displayName = displayName;
		this.email = email;
		this.roles = rolesValue(roles);
		this.lastLoginAt = now;
		this.updatedAt = now;
	}

	/**
	 * Converts this entity to the public read model.
	 * @return instructor view
	 */
	public InstructorView toView() {
		return new InstructorView(this.id, this.username, this.displayName, this.email, this.roles, this.firstLoginAt,
				this.lastLoginAt);
	}

	private static String rolesValue(Set<String> roles) {
		return roles.stream().sorted().collect(Collectors.joining(","));
	}

}
