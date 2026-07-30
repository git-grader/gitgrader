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

import java.security.PublicKey;

import org.jspecify.annotations.Nullable;

/**
 * A validated SSH public key, together with everything derived from it.
 *
 * <p>
 * Producing an instance is the only way to get past {@link SshKeyParser}, so the
 * existence of one of these objects is itself the proof that the material parsed as a
 * public key, matched the configured algorithm allow-list and met the minimum strength.
 *
 * @param keyType SSH algorithm identifier, for example {@code ssh-ed25519}
 * @param encoded the single-line OpenSSH representation, normalised without the comment
 * @param fingerprint OpenSSH SHA-256 fingerprint, for example {@code SHA256:abc...}
 * @param keyBits key size in bits, or {@code null} when the algorithm has a fixed size
 * @param comment the trailing comment as submitted, or {@code null}
 * @param publicKey the parsed JCA key, used for signature verification
 */
public record SshPublicKey(String keyType, String encoded, String fingerprint, @Nullable Integer keyBits,
		@Nullable String comment, PublicKey publicKey) {

	/**
	 * The algorithm this project recommends.
	 *
	 * <p>
	 * Ed25519 is small, fast, has no parameter choices to get wrong, and is supported by
	 * every OpenSSH release that also supports SSH commit signing, which students need
	 * anyway.
	 */
	public static final String PREFERRED_KEY_TYPE = "ssh-ed25519";

	/**
	 * Whether this key uses the recommended algorithm.
	 * @return true for Ed25519 keys
	 */
	public boolean isPreferredType() {
		return PREFERRED_KEY_TYPE.equals(this.keyType);
	}

	/**
	 * A short fingerprint form for display in dense tables.
	 *
	 * <p>
	 * Truncation is for layout only. Any comparison, lookup or authorization decision
	 * uses the full {@link #fingerprint()}; a truncated fingerprint is not unique enough
	 * to identify a key.
	 * @return an abbreviated fingerprint
	 */
	public String shortFingerprint() {
		return Fingerprints.abbreviate(this.fingerprint);
	}

}
