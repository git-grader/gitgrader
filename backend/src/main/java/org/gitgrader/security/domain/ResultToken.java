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

package org.gitgrader.security.domain;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

/**
 * Persisted state of a result token.
 */
@Entity
@Table(name = "result_tokens")
public class ResultToken {

	@Id
	private UUID id;

	private UUID submissionId;

	private String tokenHash;

	private String tokenPrefix;

	private Instant issuedAt;

	private @Nullable Instant expiresAt;

	private @Nullable Instant revokedAt;

	private @Nullable String revokedBy;

	private @Nullable Instant lastAccessAt;

	private long accessCount;

	protected ResultToken() {
	}

	public ResultToken(UUID id, UUID submissionId, String tokenHash, String tokenPrefix, Instant issuedAt,
			@Nullable Instant expiresAt) {
		this.id = id;
		this.submissionId = submissionId;
		this.tokenHash = tokenHash;
		this.tokenPrefix = tokenPrefix;
		this.issuedAt = issuedAt;
		this.expiresAt = expiresAt;
		this.accessCount = 0;
	}

	public UUID id() {
		return this.id;
	}

	public UUID submissionId() {
		return this.submissionId;
	}

	public String tokenHash() {
		return this.tokenHash;
	}

	public String tokenPrefix() {
		return this.tokenPrefix;
	}

	public Instant issuedAt() {
		return this.issuedAt;
	}

	public @Nullable Instant expiresAt() {
		return this.expiresAt;
	}

	public @Nullable Instant revokedAt() {
		return this.revokedAt;
	}

	public @Nullable String revokedBy() {
		return this.revokedBy;
	}

	public @Nullable Instant lastAccessAt() {
		return this.lastAccessAt;
	}

	public long accessCount() {
		return this.accessCount;
	}

	public void recordAccess(Instant now) {
		this.lastAccessAt = now;
		this.accessCount++;
	}

	public void revoke(String actor, Instant now) {
		this.revokedBy = actor;
		this.revokedAt = now;
	}

	/**
	 * Whether this token may still open its result page.
	 * @param now the current instant
	 * @return true when the token is neither revoked nor expired
	 */
	public boolean isValid(Instant now) {
		boolean revoked = this.revokedAt != null;
		boolean expired = this.expiresAt != null && now.isAfter(this.expiresAt);
		return !revoked && !expired;
	}

}
