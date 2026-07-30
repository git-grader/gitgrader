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

package org.gitgrader.sshkeys;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * A registered key as other modules and the API see it.
 *
 * <p>
 * Carries the full {@link #publicKey()} because a public key is not a secret and
 * instructors need to be able to compare it against what a student reports. It carries no
 * private material of any kind, because none is ever accepted or stored.
 *
 * @param id stable key identifier
 * @param studentId owner of the key
 * @param label human readable name chosen by the owner
 * @param keyType SSH algorithm, for example {@code ssh-ed25519}
 * @param publicKey canonical single-line OpenSSH representation
 * @param fingerprint OpenSSH SHA-256 fingerprint
 * @param keyBits key size, or {@code null} for fixed-size algorithms
 * @param comment trailing comment supplied by the owner
 * @param status current lifecycle state
 * @param origin how the key came to be registered
 * @param addedBy who registered it, when that was not the student
 * @param revokedAt when it stopped being valid
 * @param revocationReason why it stopped being valid
 * @param replacedById the successor key, when this one was exchanged
 * @param lastUsedAt when it last authenticated or validated a signature
 * @param createdAt when it was registered
 */
public record SshKeyView(UUID id, UUID studentId, String label, String keyType, String publicKey, String fingerprint,
		@Nullable Integer keyBits, @Nullable String comment, SshKeyStatus status, SshKeyOrigin origin,
		@Nullable String addedBy, @Nullable Instant revokedAt, @Nullable String revocationReason,
		@Nullable UUID replacedById, @Nullable Instant lastUsedAt, Instant createdAt) {

	/**
	 * Whether this key can be used right now.
	 * @return true only while the key is active
	 */
	public boolean usable() {
		return this.status.isUsable();
	}

	/**
	 * An abbreviated fingerprint for dense tables.
	 *
	 * <p>
	 * Display only. Never use it for comparison: it is not unique enough to identify a
	 * key.
	 * @return the shortened fingerprint
	 */
	public String shortFingerprint() {
		return Fingerprints.abbreviate(this.fingerprint);
	}

}
