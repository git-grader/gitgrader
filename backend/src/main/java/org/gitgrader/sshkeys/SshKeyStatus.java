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

/**
 * Lifecycle state of a registered SSH public key.
 *
 * <p>
 * A key is never deleted. Submissions record the key that signed them, and an examination
 * result may be questioned long after a student has rotated their keys, so the row has to
 * survive for the attribution to stay explainable.
 */
public enum SshKeyStatus {

	/** Usable for both transport authentication and commit signatures. */
	ACTIVE,

	/** Withdrawn permanently, typically because the private key may be compromised. */
	REVOKED,

	/** Superseded by a newer key; {@code replaced_by_id} points at the successor. */
	REPLACED,

	/** Temporarily disabled, for example while an irregularity is investigated. */
	SUSPENDED;

	/**
	 * Whether a key in this state may be used right now.
	 *
	 * <p>
	 * Only {@link #ACTIVE} qualifies. In particular {@link #REPLACED} does not: after a
	 * key exchange the old key must stop working immediately, otherwise the exchange
	 * would provide no security benefit at all.
	 * @return true when the key may authenticate a connection or validate a signature
	 */
	public boolean isUsable() {
		return this == ACTIVE;
	}

	/**
	 * Whether this state is a terminal one.
	 * @return true when the key can never become usable again
	 */
	public boolean isTerminal() {
		return this == REVOKED || this == REPLACED;
	}

}
