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

package org.gitgrader.git;

import org.jspecify.annotations.Nullable;

/**
 * The outcome of checking one commit's signature.
 *
 * <p>
 * Two independent questions are answered here and they must not be conflated:
 *
 * <ol>
 * <li><em>Is the signature cryptographically valid?</em> Answered by JGit against the
 * SSHSIG blob in the commit's {@code gpgsig} header.</li>
 * <li><em>Does the signing key belong to the student we are expecting?</em> Answered by
 * GitGrader against its own key registry.</li>
 * </ol>
 *
 * <p>
 * A signature can be perfectly valid and still be rejected, because it was made with
 * somebody else's key or with a key that has since been revoked. {@link #VERIFIED} is the
 * only value that means both questions were answered yes.
 *
 * @param status the verdict
 * @param keyFingerprint fingerprint of the key that produced the signature, when one
 * could be recovered
 * @param detail an instructor-facing explanation; never shown to a student verbatim
 */
public record CommitSignatureResult(CommitSignatureStatus status, @Nullable String keyFingerprint,
		@Nullable String detail) {

	/**
	 * Whether the commit may be accepted on signature grounds.
	 * @return true only when the signature is valid and owned by the expected student
	 */
	public boolean isAcceptable() {
		return this.status == CommitSignatureStatus.VERIFIED;
	}

	/**
	 * Builds a verified result.
	 * @param fingerprint the signing key fingerprint
	 * @return a verified result
	 */
	public static CommitSignatureResult verified(String fingerprint) {
		return new CommitSignatureResult(CommitSignatureStatus.VERIFIED, fingerprint, null);
	}

	/**
	 * Builds a rejection.
	 * @param status why the signature was not acceptable
	 * @param fingerprint the signing key fingerprint if one was recovered
	 * @param detail instructor-facing explanation
	 * @return a rejecting result
	 */
	public static CommitSignatureResult rejected(CommitSignatureStatus status, @Nullable String fingerprint,
			@Nullable String detail) {
		return new CommitSignatureResult(status, fingerprint, detail);
	}

	/**
	 * The possible verdicts, mirroring the {@code submissions.signature_status} column.
	 */
	public enum CommitSignatureStatus {

		/**
		 * Valid signature made by a key registered to this student.
		 *
		 * <p>
		 * This is what the UI renders as a green {@code Verified} badge, deliberately
		 * matching what developers already recognise from the large forges. It asserts
		 * only that the commit was signed with a registered key. It is
		 * <strong>not</strong> evidence about how the work was produced, and no part of
		 * this product may present it as such.
		 */
		VERIFIED,

		/** The commit carries no signature at all. */
		UNSIGNED,

		/** A signature is present but does not verify against the commit payload. */
		INVALID,

		/** The signing key is valid but is not registered to anyone here. */
		UNKNOWN_KEY,

		/** The signing key is registered but has been revoked or suspended. */
		KEY_REVOKED,

		/** The signing key is registered to a different student. */
		WRONG_OWNER

	}

}
