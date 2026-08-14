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

@Entity
@Table(name = "registration_attempts")
public class RegistrationAttempt {

	@Id
	private UUID id;

	private Instant attemptedAt;

	private String ipHash;

	private String outcome;

	private @Nullable String reason;

	private @Nullable String studentNumberHash;

	private @Nullable String emailHash;

	protected RegistrationAttempt() {
	}

	public RegistrationAttempt(UUID id, Instant attemptedAt, String ipHash, String outcome, @Nullable String reason,
			@Nullable String studentNumberHash, @Nullable String emailHash) {
		this.id = id;
		this.attemptedAt = attemptedAt;
		this.ipHash = ipHash;
		this.outcome = outcome;
		this.reason = reason;
		this.studentNumberHash = studentNumberHash;
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

	public @Nullable String getStudentNumberHash() {
		return this.studentNumberHash;
	}

	public @Nullable String getEmailHash() {
		return this.emailHash;
	}

}
