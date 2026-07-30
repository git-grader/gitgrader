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

import java.io.IOException;
import java.security.PublicKey;

import org.eclipse.jgit.lib.GpgConfig;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.signing.ssh.SigningKeyDatabase;

/**
 * A signing key database that answers "yes" to every key.
 *
 * <p>
 * <strong>This is not as alarming as it looks. Read this before changing it.</strong>
 *
 * <p>
 * JGit's SSHSIG verifier delegates two different decisions to a
 * {@link SigningKeyDatabase}: the cryptographic question of whether a signature matches
 * the payload, and the policy question of whether the signing key is <em>allowed</em>.
 * Out of the box the policy question is answered from OpenSSH's
 * {@code ~/.ssh/allowed_signers} file, which is a per-user file on the machine running
 * the verification. That model does not fit a server that verifies signatures on behalf
 * of thousands of students, and maintaining a generated {@code allowed_signers} file
 * would introduce a second source of truth next to the {@code ssh_keys} table.
 *
 * <p>
 * GitGrader therefore splits the two decisions explicitly:
 *
 * <ul>
 * <li><strong>JGit owns the cryptography.</strong> It parses the SSHSIG blob, rebuilds
 * the signed payload, and checks the signature. That is the part this project must never
 * reimplement.</li>
 * <li><strong>GitGrader owns the authorization.</strong> {@code CommitSignatureVerifier}
 * takes the fingerprint JGit recovered and resolves it against the key registry: the key
 * must exist, must have been valid at the time, and must belong to the student whose SSH
 * key opened the connection.</li>
 * </ul>
 *
 * <p>
 * Because the authorization decision is made afterwards and independently, this class
 * must not also try to make it - a second, weaker copy of the rule is how the two drift
 * apart. It exists purely so that JGit's policy hook does not veto a signature before the
 * real check runs.
 *
 * <p>
 * The consequence to keep in mind: a {@code SignatureVerification} obtained with this
 * database installed means "the signature is cryptographically sound", never "this commit
 * is acceptable". Nothing may treat the former as the latter.
 */
final class CryptographyOnlySigningKeyDatabase implements SigningKeyDatabase {

	/**
	 * Returned as the "principal" for every key.
	 *
	 * <p>
	 * JGit only needs a non-null value to consider the policy check passed. Returning a
	 * self-describing constant means that if this string ever surfaces in a log or a
	 * database column, it is immediately obvious where it came from.
	 */
	private static final String PRINCIPAL = "gitgrader:authorization-checked-separately";

	@Override
	public boolean isRevoked(Repository repository, GpgConfig config, PublicKey key) throws IOException {
		// Revocation lives in the ssh_keys table, which has per-student state and a
		// revocation timestamp. An OpenSSH KRL cannot express that, so this always
		// answers false and CommitSignatureVerifier applies the real rule.
		return false;
	}

	@Override
	public String isAllowed(Repository repository, GpgConfig config, PublicKey key, String namespace, PersonIdent ident)
			throws IOException {
		return PRINCIPAL;
	}

}
