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

import java.util.UUID;

import org.gitgrader.git.CommitSignatureResult;

/**
 * Decides whether a signing key belongs to the student we are expecting.
 *
 * <p>
 * This is the authorization half of commit signature verification, kept behind its own
 * interface so that it can be exercised without a database and so that the rule lives in
 * exactly one place. The cryptographic half is JGit's job and happens before this is
 * called.
 */
public interface SigningKeyOwnership {

	/**
	 * Resolves a signing key fingerprint to a verdict.
	 *
	 * <p>
	 * Implementations must distinguish the failure modes rather than collapsing them into
	 * one rejection: "we have never seen this key", "this key was revoked" and "this key
	 * belongs to somebody else" lead to very different conversations with a student, and
	 * only the last one is a reason to look closely at the submission.
	 * @param fingerprint the OpenSSH SHA-256 fingerprint recovered from the signature
	 * @param expectedStudentId the student whose SSH key opened the connection
	 * @return {@code VERIFIED} when the key is registered, usable and owned by that
	 * student, otherwise the specific reason it is not
	 */
	CommitSignatureResult authorize(String fingerprint, UUID expectedStudentId);

}
