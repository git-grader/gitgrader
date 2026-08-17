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

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditService;
import org.gitgrader.configuration.AppProperties;
import org.gitgrader.sshkeys.SshKeyOrigin;
import org.gitgrader.sshkeys.SshKeyParser;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.gitgrader.sshkeys.SshKeyRejectedException;
import org.gitgrader.sshkeys.SshKeyRejectionReason;
import org.gitgrader.sshkeys.SshKeyStatus;
import org.gitgrader.sshkeys.SshKeyView;
import org.gitgrader.sshkeys.SshPublicKey;
import org.gitgrader.sshkeys.domain.SshKeyRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link SshKeyRegistry}, enforcing the key lifecycle rules.
 */
@Service
@Transactional
public class DefaultSshKeyRegistry implements SshKeyRegistry {

	private static final Logger logger = LoggerFactory.getLogger(DefaultSshKeyRegistry.class);

	private final SshKeyRepository repository;

	private final SshKeyParser parser;

	private final AuditService auditService;

	private final AppProperties appProperties;

	private final Clock clock;

	public DefaultSshKeyRegistry(SshKeyRepository repository, SshKeyParser parser, AuditService auditService,
			AppProperties appProperties, Clock clock) {
		this.repository = repository;
		this.parser = parser;
		this.auditService = auditService;
		this.appProperties = appProperties;
		this.clock = clock;
	}

	@Override
	public SshKeyView register(UUID studentId, String label, String submittedKey, SshKeyOrigin origin,
			@Nullable String actor) {
		SshPublicKey parsed = this.parser.parse(submittedKey);
		requireUnusedFingerprint(parsed);
		requireHeadroom(studentId);

		SshKeyRecord record = this.repository
			.save(new SshKeyRecord(studentId, label, parsed, origin, actor, this.clock));
		this.auditService.record(AuditRecord.of(AuditEventType.SSH_KEY_ADDED)
			.subject("SshKey", record.id().toString())
			.with("studentId", studentId.toString())
			.with("fingerprint", record.fingerprint())
			.with("keyType", record.keyType())
			.with("origin", origin.name())
			.build());
		return toView(record);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<SshKeyView> findUsableByFingerprint(String fingerprint) {
		return this.repository.findByFingerprintAndStatus(fingerprint, SshKeyStatus.ACTIVE)
			.map(DefaultSshKeyRegistry::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<SshKeyView> findAnyByFingerprint(String fingerprint) {
		return this.repository.findByFingerprint(fingerprint).map(DefaultSshKeyRegistry::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SshKeyView> findAllForStudent(UUID studentId) {
		return this.repository.findByStudentIdOrderByCreatedAtDesc(studentId)
			.stream()
			.map(DefaultSshKeyRegistry::toView)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<SshKeyView> findActiveForStudent(UUID studentId) {
		return this.repository.findByStudentIdAndStatus(studentId, SshKeyStatus.ACTIVE)
			.stream()
			.map(DefaultSshKeyRegistry::toView)
			.toList();
	}

	@Override
	public SshKeyView revoke(UUID studentId, UUID keyId, String reason, String actor) {
		SshKeyRecord record = require(studentId, keyId);
		record.revoke(reason, actor, this.clock);
		this.auditService.record(AuditRecord.of(AuditEventType.SSH_KEY_REVOKED)
			.subject("SshKey", keyId.toString())
			.with("studentId", record.studentId().toString())
			.with("fingerprint", record.fingerprint())
			.with("reason", reason)
			.build());
		logger.info("Revoked SSH key {} for student {}", record.fingerprint(), record.studentId());
		return toView(this.repository.save(record));
	}

	@Override
	public SshKeyView replace(UUID studentId, UUID keyId, String label, String submittedKey, String reason,
			String actor) {
		SshKeyRecord outgoing = require(studentId, keyId);
		SshPublicKey parsed = this.parser.parse(submittedKey);
		requireUnusedFingerprint(parsed);

		// Register the successor first so that a rejected replacement leaves the student
		// with a working key. Both statements share one transaction, so the window in
		// which two usable keys exist never becomes visible to another request.
		SshKeyRecord incoming = this.repository
			.save(new SshKeyRecord(outgoing.studentId(), label, parsed, SshKeyOrigin.INSTRUCTOR, actor, this.clock));
		outgoing.replaceWith(incoming.id(), reason, actor, this.clock);
		this.repository.save(outgoing);

		this.auditService.record(AuditRecord.of(AuditEventType.SSH_KEY_REPLACED)
			.subject("SshKey", keyId.toString())
			.with("studentId", outgoing.studentId().toString())
			.with("previousFingerprint", outgoing.fingerprint())
			.with("newFingerprint", incoming.fingerprint())
			.with("reason", reason)
			.build());
		return toView(incoming);
	}

	@Override
	public SshKeyView suspend(UUID keyId, String reason, String actor) {
		SshKeyRecord record = require(keyId);
		record.suspend(reason, actor, this.clock);
		return toView(this.repository.save(record));
	}

	@Override
	public SshKeyView reinstate(UUID keyId, String actor) {
		SshKeyRecord record = require(keyId);
		record.reinstate(this.clock);
		this.auditService.record(AuditRecord.of(AuditEventType.SSH_KEY_REINSTATED)
			.subject("SshKey", keyId.toString())
			.with("actor", actor)
			.build());
		return toView(this.repository.save(record));
	}

	@Override
	public void recordUsage(UUID keyId) {
		this.repository.findById(keyId).ifPresent((record) -> {
			record.markUsed(this.clock);
			this.repository.save(record);
		});
	}

	/**
	 * Refuses a fingerprint that is already known, in any state.
	 * @param parsed the validated key
	 */
	private void requireUnusedFingerprint(SshPublicKey parsed) {
		if (this.repository.existsByFingerprint(parsed.fingerprint())) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.DUPLICATE_FINGERPRINT);
		}
	}

	private void requireHeadroom(UUID studentId) {
		long active = this.repository.countActiveForStudent(studentId);
		if (active >= this.appProperties.registration().maxKeysPerStudent()) {
			throw new SshKeyRejectedException(SshKeyRejectionReason.TOO_MANY_KEYS);
		}
	}

	private SshKeyRecord require(UUID keyId) {
		return this.repository.findById(keyId)
			.orElseThrow(() -> new IllegalArgumentException("No SSH key with id " + keyId));
	}

	/**
	 * Resolves a key that must belong to the named student.
	 *
	 * <p>
	 * A key that exists but belongs to somebody else is reported exactly like one that
	 * does not exist, so the caller learns nothing about another student's keys.
	 * @param studentId the expected owner
	 * @param keyId the key
	 * @return the key
	 */
	private SshKeyRecord require(UUID studentId, UUID keyId) {
		SshKeyRecord record = require(keyId);
		if (!record.studentId().equals(studentId)) {
			throw new IllegalArgumentException("No SSH key with id " + keyId + " for student " + studentId);
		}
		return record;
	}

	private static SshKeyView toView(SshKeyRecord record) {
		return new SshKeyView(record.id(), record.studentId(), record.label(), record.keyType(), record.publicKey(),
				record.fingerprint(), record.keyBits(), record.comment(), record.status(), record.addedVia(),
				record.addedBy(), record.revokedAt(), record.revocationReason(), record.replacedById(),
				record.lastUsedAt(), record.createdAt());
	}

}
