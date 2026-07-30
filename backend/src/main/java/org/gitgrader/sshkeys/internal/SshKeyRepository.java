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

package org.gitgrader.sshkeys.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.sshkeys.SshKeyStatus;
import org.gitgrader.sshkeys.domain.SshKeyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Persistence access for registered SSH keys.
 */
public interface SshKeyRepository extends JpaRepository<SshKeyRecord, UUID> {

	/**
	 * Finds a key by fingerprint regardless of its state.
	 * @param fingerprint OpenSSH SHA-256 fingerprint
	 * @return the key when it exists
	 */
	Optional<SshKeyRecord> findByFingerprint(String fingerprint);

	/**
	 * Finds a key by fingerprint only when it may currently be used.
	 * @param fingerprint OpenSSH SHA-256 fingerprint
	 * @param status the state to require
	 * @return the key when it exists in that state
	 */
	Optional<SshKeyRecord> findByFingerprintAndStatus(String fingerprint, SshKeyStatus status);

	/**
	 * Lists every key a student ever held, newest first.
	 * @param studentId the owner
	 * @return all keys for the student
	 */
	List<SshKeyRecord> findByStudentIdOrderByCreatedAtDesc(UUID studentId);

	/**
	 * Lists a student's keys in one state.
	 * @param studentId the owner
	 * @param status the state to filter on
	 * @return matching keys
	 */
	List<SshKeyRecord> findByStudentIdAndStatus(UUID studentId, SshKeyStatus status);

	/**
	 * Counts how many keys a student can currently use.
	 * @param studentId the owner
	 * @return the number of active keys
	 */
	@Query("SELECT count(k) FROM SshKeyRecord k WHERE k.studentId = :studentId AND k.status = 'ACTIVE'")
	long countActiveForStudent(@Param("studentId") UUID studentId);

	/**
	 * Whether a fingerprint is already known, in any state.
	 *
	 * <p>
	 * Deliberately not restricted to active keys. A fingerprint is globally unique in
	 * this schema, and re-registering a previously revoked key would silently resurrect
	 * material that was withdrawn for a reason.
	 * @param fingerprint OpenSSH SHA-256 fingerprint
	 * @return true when the fingerprint has ever been registered
	 */
	boolean existsByFingerprint(String fingerprint);

}
