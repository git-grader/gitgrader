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

import java.security.PublicKey;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.gitgrader.sshkeys.SshKeyOrigin;
import org.gitgrader.sshkeys.SshKeyStatus;
import org.gitgrader.sshkeys.SshPublicKey;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for the {@link SshKeyRecord} lifecycle.
 *
 * <p>
 * These rules are the reason an old submission stays explainable and a compromised key
 * stops working immediately, so each transition is asserted on its own rather than
 * through a single happy-path walk.
 */
class SshKeyRecordTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC);

	private static final UUID STUDENT = UUID.randomUUID();

	@Test
	@DisplayName("a new key starts active and usable")
	void startsActive() {
		SshKeyRecord key = newKey();

		assertThat(key.status()).isEqualTo(SshKeyStatus.ACTIVE);
		assertThat(key.isUsable()).isTrue();
		assertThat(key.revokedAt()).isNull();
		assertThat(key.createdAt()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
	}

	@Test
	@DisplayName("revoking stamps the timestamp the database constraint requires")
	void revokeStampsTimestamp() {
		SshKeyRecord key = newKey();

		key.revoke("private key may be compromised", "instructor.a", CLOCK);

		// ssh_keys_revocation_consistency enforces that status and revoked_at move
		// together; leaving one unset would make the row unsavable.
		assertThat(key.status()).isEqualTo(SshKeyStatus.REVOKED);
		assertThat(key.revokedAt()).isNotNull();
		assertThat(key.revokedBy()).isEqualTo("instructor.a");
		assertThat(key.revocationReason()).isEqualTo("private key may be compromised");
		assertThat(key.isUsable()).isFalse();
	}

	@Test
	@DisplayName("a replaced key stops being usable immediately")
	void replacedKeyStopsWorkingAtOnce() {
		SshKeyRecord key = newKey();
		UUID successor = UUID.randomUUID();

		key.replaceWith(successor, "lost laptop", "instructor.a", CLOCK);

		// A grace period here would defeat the entire point of an exchange performed
		// because the old private key may be in someone else's hands.
		assertThat(key.status()).isEqualTo(SshKeyStatus.REPLACED);
		assertThat(key.isUsable()).isFalse();
		assertThat(key.replacedById()).isEqualTo(successor);
		assertThat(key.revokedAt()).isNotNull();
	}

	@Test
	@DisplayName("suspension is reversible and does not stamp a revocation time")
	void suspensionIsReversible() {
		SshKeyRecord key = newKey();

		key.suspend("under investigation", "admin", CLOCK);
		assertThat(key.status()).isEqualTo(SshKeyStatus.SUSPENDED);
		assertThat(key.isUsable()).isFalse();
		assertThat(key.revokedAt()).isNull();

		key.reinstate(CLOCK);
		assertThat(key.status()).isEqualTo(SshKeyStatus.ACTIVE);
		assertThat(key.isUsable()).isTrue();
	}

	@Test
	@DisplayName("a revoked key can never be brought back")
	void revokedIsTerminal() {
		SshKeyRecord key = newKey();
		key.revoke("compromised", "admin", CLOCK);

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> key.revoke("again", "admin", CLOCK));
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> key.suspend("nope", "admin", CLOCK));
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> key.reinstate(CLOCK));
	}

	@Test
	@DisplayName("a replaced key can never be brought back")
	void replacedIsTerminal() {
		SshKeyRecord key = newKey();
		key.replaceWith(UUID.randomUUID(), "rotation", "admin", CLOCK);

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> key.revoke("again", "admin", CLOCK));
		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> key.reinstate(CLOCK));
	}

	@Test
	@DisplayName("only a suspended key can be reinstated")
	void reinstateRequiresSuspension() {
		SshKeyRecord active = newKey();

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> active.reinstate(CLOCK))
			.withMessageContaining("suspended");
	}

	@Test
	@DisplayName("usage is recorded without changing the lifecycle state")
	void recordsUsage() {
		SshKeyRecord key = newKey();
		assertThat(key.lastUsedAt()).isNull();

		key.markUsed(CLOCK);

		assertThat(key.lastUsedAt()).isEqualTo(Instant.parse("2026-03-01T10:00:00Z"));
		assertThat(key.status()).isEqualTo(SshKeyStatus.ACTIVE);
	}

	@Test
	@DisplayName("REPLACED is not treated as usable")
	void replacedIsNotUsable() {
		// Guards a tempting simplification: treating anything that is "not revoked" as
		// usable would silently keep exchanged keys working.
		assertThat(SshKeyStatus.ACTIVE.isUsable()).isTrue();
		assertThat(SshKeyStatus.REPLACED.isUsable()).isFalse();
		assertThat(SshKeyStatus.REVOKED.isUsable()).isFalse();
		assertThat(SshKeyStatus.SUSPENDED.isUsable()).isFalse();
	}

	private static SshKeyRecord newKey() {
		return new SshKeyRecord(STUDENT, "laptop", samplePublicKey(), SshKeyOrigin.REGISTRATION, null, CLOCK);
	}

	private static SshPublicKey samplePublicKey() {
		PublicKey stub = new PublicKey() {
			@Override
			public String getAlgorithm() {
				return "EdDSA";
			}

			@Override
			public String getFormat() {
				return "X.509";
			}

			@Override
			public byte[] getEncoded() {
				return new byte[0];
			}
		};
		return new SshPublicKey("ssh-ed25519", "ssh-ed25519 AAAAC3Nz", "SHA256:examplefingerprint", null,
				"student@example.org", stub);
	}

}
