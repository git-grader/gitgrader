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

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.gitgrader.audit.AuditService;
import org.gitgrader.configuration.AppProperties;
import org.gitgrader.configuration.GitProperties;
import org.gitgrader.sshkeys.SshKeyOrigin;
import org.gitgrader.sshkeys.SshKeyParser;
import org.gitgrader.sshkeys.SshKeyRejectedException;
import org.gitgrader.sshkeys.SshKeyRejectionReason;
import org.gitgrader.sshkeys.SshKeyStatus;
import org.gitgrader.sshkeys.SshKeyView;
import org.gitgrader.sshkeys.domain.SshKeyRecord;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link DefaultSshKeyRegistry}.
 *
 * <p>
 * These rules decide who can push as whom, so each is asserted on its own. The key
 * material is a real Ed25519 key produced by {@code ssh-keygen}, because the registry
 * delegates to the real parser and a fabricated key would never get past it.
 */
class DefaultSshKeyRegistryTest {

	private static final String ED25519 = "ssh-ed25519 "
			+ "AAAAC3NzaC1lZDI1NTE5AAAAIPkLKDHNOKp7Nnxq7eGkNcYPKi3n2uFF9aKYc41rUW0c student@example.org";

	private static final String ED25519_FINGERPRINT = "SHA256:P1N7AkIDG5MM+0K2XzEELxU1Zwa44rUmD7TwSnkCqdA";

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-01T09:00:00Z"), ZoneOffset.UTC);

	private static final UUID STUDENT = UUID.randomUUID();

	private SshKeyRepository repository;

	private DefaultSshKeyRegistry registry;

	@BeforeEach
	void setUp() {
		this.repository = mock(SshKeyRepository.class);
		AuditService audit = mock(AuditService.class);
		this.registry = new DefaultSshKeyRegistry(this.repository, parser(), audit, appProperties(), CLOCK);

		when(this.repository.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		when(this.repository.existsByFingerprint(any())).thenReturn(false);
		when(this.repository.countActiveForStudent(any())).thenReturn(0L);
	}

	@Test
	@DisplayName("registers a valid key and derives its fingerprint")
	void registersValidKey() {
		SshKeyView key = this.registry.register(STUDENT, "laptop", ED25519, SshKeyOrigin.REGISTRATION, null);

		assertThat(key.fingerprint()).isEqualTo(ED25519_FINGERPRINT);
		assertThat(key.studentId()).isEqualTo(STUDENT);
		assertThat(key.status()).isEqualTo(SshKeyStatus.ACTIVE);
		assertThat(key.usable()).isTrue();
	}

	@Test
	@DisplayName("refuses a fingerprint that is already known, in any state")
	void refusesDuplicateFingerprint() {
		// Checked against ALL keys, not just active ones. Re-registering a revoked key
		// would resurrect material that was withdrawn for a reason.
		when(this.repository.existsByFingerprint(ED25519_FINGERPRINT)).thenReturn(true);

		assertThatExceptionOfType(SshKeyRejectedException.class)
			.isThrownBy(() -> this.registry.register(STUDENT, "laptop", ED25519, SshKeyOrigin.REGISTRATION, null))
			.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(SshKeyRejectionReason.DUPLICATE_FINGERPRINT));
	}

	@Test
	@DisplayName("refuses a student who already holds the maximum number of keys")
	void refusesBeyondTheKeyLimit() {
		when(this.repository.countActiveForStudent(STUDENT)).thenReturn(5L);

		assertThatExceptionOfType(SshKeyRejectedException.class)
			.isThrownBy(() -> this.registry.register(STUDENT, "laptop", ED25519, SshKeyOrigin.REGISTRATION, null))
			.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(SshKeyRejectionReason.TOO_MANY_KEYS));
	}

	@Test
	@DisplayName("refuses a private key before it can be stored")
	void refusesPrivateKey() {
		assertThatExceptionOfType(SshKeyRejectedException.class)
			.isThrownBy(() -> this.registry.register(STUDENT, "oops",
					"-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n-----END OPENSSH PRIVATE KEY-----",
					SshKeyOrigin.REGISTRATION, null))
			.satisfies((ex) -> assertThat(ex.reason()).isEqualTo(SshKeyRejectionReason.PRIVATE_KEY_SUBMITTED));
	}

	@Test
	@DisplayName("resolves a usable key only while it is active")
	void resolvesOnlyActiveKeys() {
		SshKeyRecord active = record();
		when(this.repository.findByFingerprintAndStatus(ED25519_FINGERPRINT, SshKeyStatus.ACTIVE))
			.thenReturn(Optional.of(active));

		assertThat(this.registry.findUsableByFingerprint(ED25519_FINGERPRINT)).isPresent();

		when(this.repository.findByFingerprintAndStatus(ED25519_FINGERPRINT, SshKeyStatus.ACTIVE))
			.thenReturn(Optional.empty());
		assertThat(this.registry.findUsableByFingerprint(ED25519_FINGERPRINT)).isEmpty();
	}

	@Test
	@DisplayName("revoking records the reason and stops the key working")
	void revokeStopsTheKey() {
		SshKeyRecord stored = record();
		when(this.repository.findById(stored.id())).thenReturn(Optional.of(stored));

		SshKeyView revoked = this.registry.revoke(STUDENT, stored.id(), "laptop stolen", "instructor.a");

		assertThat(revoked.status()).isEqualTo(SshKeyStatus.REVOKED);
		assertThat(revoked.usable()).isFalse();
		assertThat(revoked.revocationReason()).isEqualTo("laptop stolen");
	}

	@Test
	@DisplayName("replacing registers the successor and retires the old key in one step")
	void replaceIsAtomic() {
		// Split into two operations, a rejected replacement would lock the student out or
		// briefly leave two usable keys. Both halves happen in one transaction.
		SshKeyRecord outgoing = record();
		when(this.repository.findById(outgoing.id())).thenReturn(Optional.of(outgoing));

		SshKeyView incoming = this.registry.replace(STUDENT, outgoing.id(), "new laptop", ED25519, "rotation",
				"instructor.a");

		assertThat(incoming.fingerprint()).isEqualTo(ED25519_FINGERPRINT);
		assertThat(incoming.status()).isEqualTo(SshKeyStatus.ACTIVE);
		assertThat(outgoing.status()).isEqualTo(SshKeyStatus.REPLACED);
		assertThat(outgoing.isUsable()).isFalse();
		assertThat(outgoing.replacedById()).isEqualTo(incoming.id());
	}

	@Test
	@DisplayName("refuses to act on a key belonging to another student")
	void refusesSomebodyElsesKey() {
		// The key identifier alone used to be enough: an instructor could revoke any key
		// by naming an unrelated student in the URL, and the request was still carried
		// out
		// against the real owner.
		SshKeyRecord stored = record();
		UUID somebodyElse = UUID.randomUUID();
		when(this.repository.findById(stored.id())).thenReturn(Optional.of(stored));

		assertThatExceptionOfType(EntityNotFoundException.class)
			.isThrownBy(() -> this.registry.revoke(somebodyElse, stored.id(), "not mine", "instructor.a"));
		assertThatExceptionOfType(EntityNotFoundException.class).isThrownBy(
				() -> this.registry.replace(somebodyElse, stored.id(), "new", ED25519, "rotation", "instructor.a"));
		assertThat(stored.status()).isEqualTo(SshKeyStatus.ACTIVE);
	}

	@Test
	@DisplayName("lists a student's keys, including retired ones")
	void listsFullHistory() {
		when(this.repository.findByStudentIdOrderByCreatedAtDesc(STUDENT)).thenReturn(List.of(record()));

		assertThat(this.registry.findAllForStudent(STUDENT)).hasSize(1);
	}

	private SshKeyRecord record() {
		return new SshKeyRecord(STUDENT, "laptop", parser().parse(ED25519), SshKeyOrigin.REGISTRATION, null, CLOCK);
	}

	private static SshKeyParser parser() {
		return new SshKeyParser(new GitProperties(true, "localhost", 2222, "0.0.0.0", 2222, "git", "/tmp/hostkey.ser",
				"/tmp/repositories", DataSize.ofMegabytes(50), DataSize.ofMegabytes(10), 2000, Set.of("ssh-ed25519"),
				true, Duration.ofMinutes(10)));
	}

	private static AppProperties appProperties() {
		return new AppProperties("GitGrader", URI.create("http://localhost:8080"), "support@example.org",
				"Example Organization", URI.create("http://localhost:8080/docs"), ZoneId.of("UTC"), "/data",
				new AppProperties.Registration(true, false, 5),
				new AppProperties.ResultTokens(256, Duration.ofDays(180), 8));
	}

}
