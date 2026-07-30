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
 * Why a submitted SSH key was refused.
 *
 * <p>
 * Each constant maps to a distinct, actionable message. A single generic "invalid key"
 * error would be the difference between a student fixing the problem in ten seconds and
 * filing a support request.
 */
public enum SshKeyRejectionReason {

	/**
	 * The submitted text is a PRIVATE key.
	 *
	 * <p>
	 * This is the one rejection that is a security incident rather than a typo. It is
	 * detected before parsing, before persistence and before any logging, and the
	 * offending material is never written anywhere. The student is told to treat the key
	 * as compromised and to generate a new one.
	 */
	PRIVATE_KEY_SUBMITTED,

	/** The text is not in OpenSSH {@code authorized_keys} format at all. */
	MALFORMED,

	/** More than one key was pasted; keys are added one at a time. */
	MULTIPLE_KEYS,

	/** The algorithm is not in the configured allow-list. */
	UNSUPPORTED_KEY_TYPE,

	/** The algorithm is structurally understood but no longer considered safe. */
	WEAK_ALGORITHM,

	/** An RSA key below the configured minimum modulus size. */
	KEY_TOO_SHORT,

	/** The same key is already registered, possibly to a different student. */
	DUPLICATE_FINGERPRINT,

	/** The student already holds the maximum number of active keys. */
	TOO_MANY_KEYS,

	/** The submitted text was empty or only whitespace. */
	EMPTY;

	/**
	 * A message safe to return to an unauthenticated caller.
	 *
	 * <p>
	 * {@link #DUPLICATE_FINGERPRINT} deliberately does not say which account already
	 * holds the key: on a public endpoint that would turn the form into an oracle for
	 * testing whether a given key is registered.
	 * @return a human readable, non-disclosing explanation
	 */
	public String publicMessage() {
		return switch (this) {
			case PRIVATE_KEY_SUBMITTED -> "That looks like a PRIVATE key. Never upload or share a private key. "
					+ "Consider the key compromised, generate a new key pair, and submit only the "
					+ "public part, which is the file ending in .pub.";
			case MALFORMED -> "This is not a valid SSH public key. It should be a single line that starts with "
					+ "a key type such as ssh-ed25519, followed by the key data.";
			case MULTIPLE_KEYS -> "Please submit exactly one key at a time.";
			case UNSUPPORTED_KEY_TYPE ->
				"This key type is not accepted. An Ed25519 key is recommended: " + "ssh-keygen -t ed25519";
			case WEAK_ALGORITHM -> "This key uses an algorithm that is no longer considered safe. "
					+ "Please generate an Ed25519 key: ssh-keygen -t ed25519";
			case KEY_TOO_SHORT ->
				"This key is too short to be accepted. Please generate an Ed25519 key: " + "ssh-keygen -t ed25519";
			case DUPLICATE_FINGERPRINT ->
				"This key cannot be used. Please generate a new key pair and submit " + "the new public key.";
			case TOO_MANY_KEYS ->
				"You already have the maximum number of active keys. " + "Revoke one before adding another.";
			case EMPTY -> "No key was provided. Paste the contents of your public key file, which is "
					+ "usually ~/.ssh/id_ed25519.pub";
		};
	}

	/**
	 * Whether this rejection should be treated as a security event rather than a
	 * validation error.
	 * @return true when the rejection warrants a raised-severity audit record
	 */
	public boolean isSecurityRelevant() {
		return this == PRIVATE_KEY_SUBMITTED || this == DUPLICATE_FINGERPRINT;
	}

}
