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

package org.gitgrader.assignments.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.gitgrader.assignments.DeadlineExtensionView;
import org.jspecify.annotations.Nullable;

/** A reasoned per-student extension that is revoked softly and never deleted. */
@Entity
@Table(name = "deadline_extensions")
public class DeadlineExtension {

	@Id
	private UUID id;

	@Column(name = "assignment_id", nullable = false)
	private UUID assignmentId;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "extended_due_at", nullable = false)
	private Instant extendedDueAt;

	@Column(nullable = false)
	private String reason;

	@Column(name = "granted_by", nullable = false)
	private String grantedBy;

	@Column(name = "granted_at", nullable = false)
	private Instant grantedAt;

	@Column(name = "revoked_at")
	private @Nullable Instant revokedAt;

	@Column(name = "revoked_by")
	private @Nullable String revokedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected DeadlineExtension() {
	}

	/**
	 * Grants a reasoned extension.
	 * @param assignmentId assignment identifier
	 * @param studentId student identifier
	 * @param extendedDueAt replacement due instant
	 * @param reason non-blank reason
	 * @param actor granting instructor
	 * @param clock application clock
	 */
	public DeadlineExtension(UUID assignmentId, UUID studentId, Instant extendedDueAt, String reason, String actor,
			Clock clock) {
		if (reason.isBlank()) {
			throw new IllegalArgumentException("Deadline extension reason must not be blank");
		}
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.assignmentId = assignmentId;
		this.studentId = studentId;
		this.extendedDueAt = extendedDueAt;
		this.reason = reason;
		this.grantedBy = actor;
		this.grantedAt = now;
		this.createdAt = now;
	}

	/**
	 * Soft-revokes the extension.
	 * @param actor revoking instructor
	 * @param clock application clock
	 */
	public void revoke(String actor, Clock clock) {
		if (this.revokedAt != null) {
			throw new IllegalStateException("Deadline extension is already revoked");
		}
		this.revokedAt = Instant.now(clock);
		this.revokedBy = actor;
	}

	/**
	 * Returns the replacement due instant.
	 * @return extended due instant
	 */
	public Instant extendedDueAt() {
		return this.extendedDueAt;
	}

	/**
	 * Converts this entity to its public read model.
	 * @return extension view
	 */
	public DeadlineExtensionView toView() {
		return new DeadlineExtensionView(this.id, this.assignmentId, this.studentId, this.extendedDueAt, this.reason,
				this.grantedBy, this.grantedAt, this.revokedAt, this.revokedBy, this.createdAt);
	}

}
