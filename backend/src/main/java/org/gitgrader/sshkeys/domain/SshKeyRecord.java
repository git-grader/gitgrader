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

package org.gitgrader.sshkeys.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.gitgrader.sshkeys.SshKeyOrigin;
import org.gitgrader.sshkeys.SshKeyStatus;
import org.gitgrader.sshkeys.SshPublicKey;
import org.jspecify.annotations.Nullable;

/**
 * A registered SSH public key and its lifecycle.
 *
 * <p>
 * The row is append-oriented: {@link #revoke} and {@link #replaceWith} move the status
 * and stamp the reason, but nothing ever deletes it. A submission stores the id of the
 * key that signed it, so removing a key would orphan the attribution on work that may
 * still be under discussion.
 */
@Entity
@Table(name = "ssh_keys")
public class SshKeyRecord {

	@Id
	private UUID id;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(nullable = false)
	private String label;

	@Column(name = "key_type", nullable = false)
	private String keyType;

	@Column(name = "public_key", nullable = false)
	private String publicKey;

	@Column(nullable = false)
	private String fingerprint;

	@Column(name = "key_bits")
	private @Nullable Integer keyBits;

	@Column(name = "comment")
	private @Nullable String comment;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SshKeyStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "added_via", nullable = false)
	private SshKeyOrigin addedVia;

	@Column(name = "added_by")
	private @Nullable String addedBy;

	@Column(name = "revoked_at")
	private @Nullable Instant revokedAt;

	@Column(name = "revoked_by")
	private @Nullable String revokedBy;

	@Column(name = "revocation_reason")
	private @Nullable String revocationReason;

	@Column(name = "replaced_by_id")
	private @Nullable UUID replacedById;

	@Column(name = "last_used_at")
	private @Nullable Instant lastUsedAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected SshKeyRecord() {
		// Required by JPA.
	}

	public SshKeyRecord(UUID studentId, String label, SshPublicKey key, SshKeyOrigin origin, @Nullable String addedBy,
			Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.studentId = studentId;
		this.label = label;
		this.keyType = key.keyType();
		this.publicKey = key.encoded();
		this.fingerprint = key.fingerprint();
		this.keyBits = key.keyBits();
		this.comment = key.comment();
		this.status = SshKeyStatus.ACTIVE;
		this.addedVia = origin;
		this.addedBy = addedBy;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID id() {
		return this.id;
	}

	public UUID studentId() {
		return this.studentId;
	}

	public String label() {
		return this.label;
	}

	public String keyType() {
		return this.keyType;
	}

	public String publicKey() {
		return this.publicKey;
	}

	public String fingerprint() {
		return this.fingerprint;
	}

	public @Nullable Integer keyBits() {
		return this.keyBits;
	}

	public @Nullable String comment() {
		return this.comment;
	}

	public SshKeyStatus status() {
		return this.status;
	}

	public SshKeyOrigin addedVia() {
		return this.addedVia;
	}

	public @Nullable String addedBy() {
		return this.addedBy;
	}

	public @Nullable Instant revokedAt() {
		return this.revokedAt;
	}

	public @Nullable String revokedBy() {
		return this.revokedBy;
	}

	public @Nullable String revocationReason() {
		return this.revocationReason;
	}

	public @Nullable UUID replacedById() {
		return this.replacedById;
	}

	public @Nullable Instant lastUsedAt() {
		return this.lastUsedAt;
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	/**
	 * Whether this key may be used for authentication or signature validation now.
	 * @return true only while the key is active
	 */
	public boolean isUsable() {
		return this.status.isUsable();
	}

	/**
	 * Withdraws the key permanently.
	 * @param reason why it was withdrawn; recorded for the audit trail
	 * @param actor who performed the revocation
	 * @param clock the application clock
	 * @throws IllegalStateException if the key already reached a terminal state
	 */
	public void revoke(String reason, String actor, Clock clock) {
		requireNotTerminal("revoke");
		this.status = SshKeyStatus.REVOKED;
		// The ssh_keys_revocation_consistency constraint requires these to move together.
		this.revokedAt = Instant.now(clock);
		this.revokedBy = actor;
		this.revocationReason = reason;
		this.updatedAt = this.revokedAt;
	}

	/**
	 * Marks this key as superseded by another.
	 *
	 * <p>
	 * The replacement becomes unusable immediately rather than at some grace period: an
	 * exchange performed because a private key may be compromised is worthless if the old
	 * key keeps working.
	 * @param successorId the key that takes over
	 * @param reason why the exchange happened
	 * @param actor who performed it
	 * @param clock the application clock
	 * @throws IllegalStateException if the key already reached a terminal state
	 */
	public void replaceWith(UUID successorId, String reason, String actor, Clock clock) {
		requireNotTerminal("replace");
		this.status = SshKeyStatus.REPLACED;
		this.replacedById = successorId;
		this.revokedAt = Instant.now(clock);
		this.revokedBy = actor;
		this.revocationReason = reason;
		this.updatedAt = this.revokedAt;
	}

	/**
	 * Temporarily disables the key without ending its life.
	 * @param reason why it was suspended
	 * @param actor who suspended it
	 * @param clock the application clock
	 * @throws IllegalStateException if the key already reached a terminal state
	 */
	public void suspend(String reason, String actor, Clock clock) {
		requireNotTerminal("suspend");
		this.status = SshKeyStatus.SUSPENDED;
		this.revocationReason = reason;
		this.revokedBy = actor;
		this.updatedAt = Instant.now(clock);
	}

	/**
	 * Returns a suspended key to service.
	 * @param clock the application clock
	 * @throws IllegalStateException if the key is not suspended
	 */
	public void reinstate(Clock clock) {
		if (this.status != SshKeyStatus.SUSPENDED) {
			throw new IllegalStateException("Only a suspended key can be reinstated, but this key is " + this.status);
		}
		this.status = SshKeyStatus.ACTIVE;
		this.revocationReason = null;
		this.revokedBy = null;
		this.updatedAt = Instant.now(clock);
	}

	/**
	 * Records that the key was just used.
	 * @param clock the application clock
	 */
	public void markUsed(Clock clock) {
		this.lastUsedAt = Instant.now(clock);
	}

	private void requireNotTerminal(String operation) {
		if (this.status.isTerminal()) {
			throw new IllegalStateException("Cannot " + operation + " a key that is already " + this.status);
		}
	}

}
