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

package org.gitgrader.git.internal;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.git.CommitSignatureResult;
import org.gitgrader.git.CommitSignatureResult.CommitSignatureStatus;
import org.gitgrader.sshkeys.SshKeyOrigin;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.gitgrader.sshkeys.SshKeyStatus;
import org.gitgrader.sshkeys.SshKeyView;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link RegisteredKeyOwnership}.
 *
 * <p>
 * This class is the authorization half of commit signature verification. JGit has already
 * decided the signature is cryptographically sound by the time these paths run, so every
 * assertion here is about <em>whose</em> key it was - which is the half a naive
 * implementation forgets, and the half that decides whether somebody can push work signed
 * with another student's key.
 */
class RegisteredKeyOwnershipTest {

	private static final String FINGERPRINT = "SHA256:examplefingerprintvalue";

	private static final UUID OWNER = UUID.randomUUID();

	private static final UUID SOMEBODY_ELSE = UUID.randomUUID();

	@Test
	@DisplayName("accepts an active key owned by the pushing student")
	void acceptsOwnActiveKey() {
		SshKeyView registered = key(OWNER, SshKeyStatus.ACTIVE);
		RegisteredKeyOwnership ownership = new RegisteredKeyOwnership(registryWith(registered));

		CommitSignatureResult result = ownership.authorize(FINGERPRINT, OWNER);

		assertThat(result.status()).isEqualTo(CommitSignatureStatus.VERIFIED);
		assertThat(result.isAcceptable()).isTrue();
		assertThat(result.keyFingerprint()).isEqualTo(FINGERPRINT);
	}

	@Test
	@DisplayName("names the registered key it resolved, so the submission can record it")
	void namesTheResolvedKey() {
		// The fingerprint alone was all that reached the submission, leaving
		// submissions.signature_key_id null on every row despite the column existing and
		// the ownership check having had the key in hand.
		SshKeyView registered = key(OWNER, SshKeyStatus.ACTIVE);
		RegisteredKeyOwnership ownership = new RegisteredKeyOwnership(registryWith(registered));

		CommitSignatureResult result = ownership.authorize(FINGERPRINT, OWNER);

		assertThat(result.signingKeyId()).isEqualTo(registered.id());
	}

	@Test
	@DisplayName("refuses a valid signature made with another student's key")
	void refusesSomebodyElsesKey() {
		// The signature verifies perfectly. It is simply not this student's key, which is
		// the case that matters most and the one a crypto-only check would wave through.
		RegisteredKeyOwnership ownership = new RegisteredKeyOwnership(
				registryWith(key(SOMEBODY_ELSE, SshKeyStatus.ACTIVE)));

		CommitSignatureResult result = ownership.authorize(FINGERPRINT, OWNER);

		assertThat(result.status()).isEqualTo(CommitSignatureStatus.WRONG_OWNER);
		assertThat(result.isAcceptable()).isFalse();
	}

	@Test
	@DisplayName("refuses a key that is registered but revoked")
	void refusesRevokedKey() {
		RegisteredKeyOwnership ownership = new RegisteredKeyOwnership(registryWith(key(OWNER, SshKeyStatus.REVOKED)));

		CommitSignatureResult result = ownership.authorize(FINGERPRINT, OWNER);

		assertThat(result.status()).isEqualTo(CommitSignatureStatus.KEY_REVOKED);
		assertThat(result.detail()).contains("REVOKED");
	}

	@Test
	@DisplayName("refuses a key that was exchanged for a newer one")
	void refusesReplacedKey() {
		RegisteredKeyOwnership ownership = new RegisteredKeyOwnership(registryWith(key(OWNER, SshKeyStatus.REPLACED)));

		CommitSignatureResult result = ownership.authorize(FINGERPRINT, OWNER);

		assertThat(result.status()).isEqualTo(CommitSignatureStatus.KEY_REVOKED);
		assertThat(result.isAcceptable()).isFalse();
	}

	@Test
	@DisplayName("refuses a suspended key")
	void refusesSuspendedKey() {
		RegisteredKeyOwnership ownership = new RegisteredKeyOwnership(registryWith(key(OWNER, SshKeyStatus.SUSPENDED)));

		assertThat(ownership.authorize(FINGERPRINT, OWNER).status()).isEqualTo(CommitSignatureStatus.KEY_REVOKED);
	}

	@Test
	@DisplayName("distinguishes an unknown key from a withdrawn one")
	void distinguishesUnknownFromWithdrawn() {
		RegisteredKeyOwnership ownership = new RegisteredKeyOwnership(registryWith(null));

		CommitSignatureResult result = ownership.authorize(FINGERPRINT, OWNER);

		assertThat(result.status()).isEqualTo(CommitSignatureStatus.UNKNOWN_KEY);
		assertThat(result.detail()).contains("not registered");
	}

	private static SshKeyView key(UUID studentId, SshKeyStatus status) {
		return new SshKeyView(UUID.randomUUID(), studentId, "laptop", "ssh-ed25519", "ssh-ed25519 AAAA", FINGERPRINT,
				null, null, status, SshKeyOrigin.REGISTRATION, null, null, null, null, null,
				Instant.parse("2026-01-01T00:00:00Z"));
	}

	/**
	 * A registry holding at most one key, wired the way the real one behaves.
	 * @param stored the key the registry knows about, or {@code null} for none
	 * @return a stub registry
	 */
	private static SshKeyRegistry registryWith(@Nullable SshKeyView stored) {
		return new SshKeyRegistry() {

			@Override
			public SshKeyView register(UUID studentId, String label, String submittedKey, SshKeyOrigin origin,
					@Nullable String actor) {
				throw new UnsupportedOperationException();
			}

			@Override
			public Optional<SshKeyView> findUsableByFingerprint(String fingerprint) {
				// Mirrors the production query, which filters on ACTIVE. Getting this
				// wrong in the stub would make the revoked-key tests pass vacuously.
				return Optional.ofNullable(stored).filter((key) -> key.status() == SshKeyStatus.ACTIVE);
			}

			@Override
			public Optional<SshKeyView> findAnyByFingerprint(String fingerprint) {
				return Optional.ofNullable(stored);
			}

			@Override
			public List<SshKeyView> findAllForStudent(UUID studentId) {
				return List.of();
			}

			@Override
			public List<SshKeyView> findActiveForStudent(UUID studentId) {
				return List.of();
			}

			@Override
			public SshKeyView revoke(UUID studentId, UUID keyId, String reason, String actor) {
				throw new UnsupportedOperationException();
			}

			@Override
			public SshKeyView replace(UUID studentId, UUID keyId, String label, String submittedKey, String reason,
					String actor) {
				throw new UnsupportedOperationException();
			}

			@Override
			public SshKeyView suspend(UUID keyId, String reason, String actor) {
				throw new UnsupportedOperationException();
			}

			@Override
			public SshKeyView reinstate(UUID keyId, String actor) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void recordUsage(UUID keyId) {
				throw new UnsupportedOperationException();
			}

		};
	}

}
