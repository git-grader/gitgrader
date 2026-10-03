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
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.jspecify.annotations.Nullable;

/**
 * Persisted state of a student-scoped results overview token.
 */
@Entity
@Table(name = "student_results_overview_tokens")
public class StudentResultsOverviewToken {

	/**
	 * Lifecycle of an overview token. Only {@link #ACTIVE} tokens resolve.
	 */
	public enum Status {

		/** Usable until it expires or is revoked. */
		ACTIVE,

		/** Withdrawn by an administrator. */
		REVOKED,

		/** Past its expiry instant. */
		EXPIRED

	}

	@Id
	private UUID id;

	private UUID studentId;

	private String tokenValue;

	private String tokenPrefix;

	private Instant createdAt;

	private @Nullable Instant expiresAt;

	private @Nullable Instant lastUsedAt;

	@Enumerated(EnumType.STRING)
	private Status status;

	private long accessCount;

	protected StudentResultsOverviewToken() {
	}

	public StudentResultsOverviewToken(UUID id, UUID studentId, String tokenValue, String tokenPrefix,
			Instant createdAt, @Nullable Instant expiresAt, Status status) {
		this.id = id;
		this.studentId = studentId;
		this.tokenValue = tokenValue;
		this.tokenPrefix = tokenPrefix;
		this.createdAt = createdAt;
		this.expiresAt = expiresAt;
		this.status = status;
		this.accessCount = 0;
	}

	public UUID id() {
		return this.id;
	}

	public UUID studentId() {
		return this.studentId;
	}

	public String tokenValue() {
		return this.tokenValue;
	}

	public String tokenPrefix() {
		return this.tokenPrefix;
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	public @Nullable Instant expiresAt() {
		return this.expiresAt;
	}

	public @Nullable Instant lastUsedAt() {
		return this.lastUsedAt;
	}

	public Status status() {
		return this.status;
	}

	public long accessCount() {
		return this.accessCount;
	}

	public void recordAccess(Instant now) {
		this.lastUsedAt = now;
		this.accessCount++;
	}

	public void revoke(Instant now) {
		this.status = Status.REVOKED;
		this.lastUsedAt = now;
	}

	public void markExpired(Instant now) {
		if (this.status == Status.ACTIVE) {
			this.status = Status.EXPIRED;
		}
	}

	public boolean isActive() {
		return this.status == Status.ACTIVE;
	}

	public boolean isExpired(Instant now) {
		return this.expiresAt != null && now.isAfter(this.expiresAt);
	}

}
