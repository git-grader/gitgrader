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

import java.security.PublicKey;
import java.util.Optional;
import java.util.UUID;

import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.server.auth.pubkey.PublickeyAuthenticator;
import org.apache.sshd.server.session.ServerSession;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentView;
import org.gitgrader.sshkeys.SshKeyParser;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.gitgrader.sshkeys.SshKeyView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Establishes who is connecting, using only the SSH key they authenticated with.
 *
 * <p>
 * The SSH user name is deliberately ignored. Every student connects as the same fixed
 * user (by default {@code git}), exactly as on the large forges, and identity comes
 * entirely from the key fingerprint. A user name supplied by the client is an
 * unauthenticated string and must never influence an authorization decision.
 *
 * <p>
 * On success the resolved student is pinned to the SSH session, so the rest of the
 * connection - repository resolution, push admission, signature ownership - all works
 * from one authenticated identity rather than re-deriving it.
 */
@Component
public class StudentKeyAuthenticator implements PublickeyAuthenticator {

	/** Session attribute holding the authenticated student. */
	public static final AttributeKey<AuthenticatedStudent> AUTHENTICATED_STUDENT = new AttributeKey<>();

	private static final Logger logger = LoggerFactory.getLogger(StudentKeyAuthenticator.class);

	private final SshKeyRegistry keyRegistry;

	private final StudentDirectory studentDirectory;

	private final SshKeyParser keyParser;

	public StudentKeyAuthenticator(SshKeyRegistry keyRegistry, StudentDirectory studentDirectory,
			SshKeyParser keyParser) {
		this.keyRegistry = keyRegistry;
		this.studentDirectory = studentDirectory;
		this.keyParser = keyParser;
	}

	@Override
	public boolean authenticate(String username, PublicKey key, ServerSession session) {
		String fingerprint = this.keyParser.fingerprintOf(key);

		Optional<SshKeyView> registered = this.keyRegistry.findUsableByFingerprint(fingerprint);
		if (registered.isEmpty()) {
			// Deliberately identical handling for "never seen" and "revoked": the client
			// learns only that the key was refused, never whether it is known here.
			logger.info("Rejected SSH authentication for unknown or unusable key {}", fingerprint);
			return false;
		}

		SshKeyView keyView = registered.get();
		Optional<StudentView> student = this.studentDirectory.findById(keyView.studentId());
		if (student.isEmpty()) {
			logger.warn("SSH key {} resolves to student {} which no longer exists", fingerprint, keyView.studentId());
			return false;
		}

		StudentView owner = student.get();
		if (!owner.status().canSubmit()) {
			logger.info("Rejected SSH authentication for student {} in status {}", owner.id(), owner.status());
			return false;
		}

		session.setAttribute(AUTHENTICATED_STUDENT,
				new AuthenticatedStudent(owner.id(), owner.fullName(), keyView.id(), fingerprint));
		this.keyRegistry.recordUsage(keyView.id());
		logger.debug("Authenticated student {} via key {}", owner.id(), fingerprint);
		return true;
	}

	/**
	 * The identity pinned to an authenticated SSH session.
	 *
	 * @param studentId the connecting student
	 * @param displayName name shown in push feedback
	 * @param transportKeyId the registered key that opened the connection
	 * @param fingerprint fingerprint of that key
	 */
	public record AuthenticatedStudent(UUID studentId, String displayName, UUID transportKeyId, String fingerprint) {
	}

}
