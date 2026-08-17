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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * The public API of the SSH key module.
 *
 * <p>
 * This is the only way other modules touch key material. In particular the {@code git}
 * module resolves a connecting student through {@link #findUsableByFingerprint} rather
 * than querying the table itself, so the rule for "may this key be used" lives in exactly
 * one place and cannot drift between the transport check and the signature check.
 */
public interface SshKeyRegistry {

	/**
	 * Registers a validated public key for a student.
	 * @param studentId the owner
	 * @param label human readable name for the key
	 * @param submittedKey the raw text the user supplied
	 * @param origin how the key is being added
	 * @param actor who is performing the registration, or {@code null} for the student
	 * @return the stored key
	 * @throws SshKeyRejectedException if the material is not an acceptable public key, is
	 * already registered, or the student is at their key limit
	 */
	SshKeyView register(UUID studentId, String label, String submittedKey, SshKeyOrigin origin, @Nullable String actor);

	/**
	 * Resolves a key that is currently allowed to authenticate or sign.
	 *
	 * <p>
	 * Returns empty for a revoked, replaced or suspended key. Callers must not fall back
	 * to a broader lookup when this is empty: that is precisely the check that stops a
	 * withdrawn key from still opening a connection.
	 * @param fingerprint OpenSSH SHA-256 fingerprint
	 * @return the key when it exists and is usable
	 */
	Optional<SshKeyView> findUsableByFingerprint(String fingerprint);

	/**
	 * Resolves a key regardless of its state.
	 *
	 * <p>
	 * Used to explain <em>why</em> something was refused, and to render the key that
	 * signed a historical submission. Never use it to make an access decision.
	 * @param fingerprint OpenSSH SHA-256 fingerprint
	 * @return the key when it exists in any state
	 */
	Optional<SshKeyView> findAnyByFingerprint(String fingerprint);

	/**
	 * Lists every key ever held by a student, newest first.
	 * @param studentId the owner
	 * @return all keys, including revoked and replaced ones
	 */
	List<SshKeyView> findAllForStudent(UUID studentId);

	/**
	 * Lists the keys a student can currently use.
	 * @param studentId the owner
	 * @return the active keys
	 */
	List<SshKeyView> findActiveForStudent(UUID studentId);

	/**
	 * Withdraws a key permanently.
	 *
	 * <p>
	 * Scoped to the owner on purpose: the key identifier alone is enough to act on
	 * somebody else's key, and the caller always knows whose key it means.
	 * @param studentId the owner the caller believes the key belongs to
	 * @param keyId the key
	 * @param reason why it is being withdrawn
	 * @param actor who is withdrawing it
	 * @return the updated key
	 * @throws IllegalArgumentException when no such key exists for that student
	 */
	SshKeyView revoke(UUID studentId, UUID keyId, String reason, String actor);

	/**
	 * Exchanges a key for a new one in a single operation.
	 *
	 * <p>
	 * Atomic on purpose. Revoking first and adding second would leave a student locked
	 * out if the new key turned out to be invalid, and adding first and revoking second
	 * would briefly leave both usable.
	 * @param studentId the owner the caller believes the key belongs to
	 * @param keyId the key being replaced
	 * @param label label for the replacement
	 * @param submittedKey raw text of the replacement public key
	 * @param reason why the exchange is happening
	 * @param actor who is performing it
	 * @return the newly registered key
	 * @throws IllegalArgumentException when no such key exists for that student
	 */
	SshKeyView replace(UUID studentId, UUID keyId, String label, String submittedKey, String reason, String actor);

	/**
	 * Temporarily disables a key.
	 * @param keyId the key
	 * @param reason why it is being suspended
	 * @param actor who is suspending it
	 * @return the updated key
	 */
	SshKeyView suspend(UUID keyId, String reason, String actor);

	/**
	 * Returns a suspended key to service.
	 * @param keyId the key
	 * @param actor who is reinstating it
	 * @return the updated key
	 */
	SshKeyView reinstate(UUID keyId, String actor);

	/**
	 * Records that a key was just used to authenticate.
	 * @param keyId the key
	 */
	void recordUsage(UUID keyId);

}
