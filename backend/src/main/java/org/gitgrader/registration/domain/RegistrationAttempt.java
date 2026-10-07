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

package org.gitgrader.registration.domain;

import java.time.Instant;
import java.util.UUID;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

/**
 * One recorded attempt to self-register, successful or not.
 *
 * <p>
 * The row exists so an operator can see what registration traffic looked like, and it is
 * written for refused attempts as well as accepted ones — a form that answers 400 without
 * recording anything leaves nothing to diagnose. The identifying columns are hashed
 * rather than stored: the address, username and email are all attacker-supplied values
 * that the service has no need to retain, and keeping the hashes still permits
 * correlating repeated attempts by the same caller.
 */
@Entity
@Table(name = "registration_attempts")
public class RegistrationAttempt {

	@Id
	private UUID id;

	private Instant attemptedAt;

	private String ipHash;

	private String outcome;

	private @Nullable String reason;

	private @Nullable String studentUsernameHash;

	private @Nullable String emailHash;

	protected RegistrationAttempt() {
	}

	public RegistrationAttempt(UUID id, Instant attemptedAt, String ipHash, String outcome, @Nullable String reason,
			@Nullable String studentUsernameHash, @Nullable String emailHash) {
		this.id = id;
		this.attemptedAt = attemptedAt;
		this.ipHash = ipHash;
		this.outcome = outcome;
		this.reason = reason;
		this.studentUsernameHash = studentUsernameHash;
		this.emailHash = emailHash;
	}

	// getters
	public UUID getId() {
		return this.id;
	}

	public Instant getAttemptedAt() {
		return this.attemptedAt;
	}

	public String getIpHash() {
		return this.ipHash;
	}

	public String getOutcome() {
		return this.outcome;
	}

	public @Nullable String getReason() {
		return this.reason;
	}

	public @Nullable String getStudentUsernameHash() {
		return this.studentUsernameHash;
	}

	public @Nullable String getEmailHash() {
		return this.emailHash;
	}

}
