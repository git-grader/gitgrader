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

import java.util.Optional;
import java.util.UUID;

import org.gitgrader.git.CommitSignatureResult;
import org.gitgrader.git.CommitSignatureResult.CommitSignatureStatus;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.gitgrader.sshkeys.SshKeyView;
import org.springframework.stereotype.Component;

/**
 * Resolves a signing key against the registry to decide whose commit this is.
 *
 * <p>
 * Runs after JGit has already confirmed the signature is cryptographically sound. Its
 * only job is the question JGit cannot answer: does this key belong to the student whose
 * SSH key opened the connection, and was that key allowed to sign?
 */
@Component
public class RegisteredKeyOwnership implements SigningKeyOwnership {

	private final SshKeyRegistry keyRegistry;

	public RegisteredKeyOwnership(SshKeyRegistry keyRegistry) {
		this.keyRegistry = keyRegistry;
	}

	@Override
	public CommitSignatureResult authorize(String fingerprint, UUID expectedStudentId) {
		Optional<SshKeyView> usable = this.keyRegistry.findUsableByFingerprint(fingerprint);
		if (usable.isEmpty()) {
			return describeUnusableKey(fingerprint);
		}

		SshKeyView key = usable.get();
		if (!key.studentId().equals(expectedStudentId)) {
			// The transport and the signature disagree about who this is. That is the one
			// outcome here worth a human look, so it gets its own status rather than
			// being
			// folded into a generic rejection.
			return CommitSignatureResult.rejected(CommitSignatureStatus.WRONG_OWNER, fingerprint,
					"The signing key is registered to a different student than the one who pushed");
		}
		return CommitSignatureResult.verified(fingerprint);
	}

	/**
	 * Explains why a key that is not usable was refused.
	 *
	 * <p>
	 * Distinguishing "never registered" from "registered but withdrawn" costs one extra
	 * lookup and saves a support conversation: a student who rotated a key gets told
	 * exactly that, instead of a generic failure.
	 * @param fingerprint the fingerprint recovered from the signature
	 * @return the specific rejection
	 */
	private CommitSignatureResult describeUnusableKey(String fingerprint) {
		return this.keyRegistry.findAnyByFingerprint(fingerprint)
			.map((known) -> CommitSignatureResult.rejected(CommitSignatureStatus.KEY_REVOKED, fingerprint,
					"The signing key is registered but is " + known.status() + " and may no longer be used to sign"))
			.orElseGet(() -> CommitSignatureResult.rejected(CommitSignatureStatus.UNKNOWN_KEY, fingerprint,
					"The signing key is not registered to any student on this instance"));
	}

}
