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

package org.gitgrader.submissions;

/**
 * The recorded signature outcome of a submission.
 *
 * <p>
 * This is the persisted fact, stored in {@code submissions.signature_status}. The
 * {@code git} module computes the outcome at push time and maps its own verification
 * result onto these values; the two are kept separate because one is a transient decision
 * and this one has to still make sense years later.
 */
public enum SignatureVerdict {

	/**
	 * Signed with a key registered to this student.
	 *
	 * <p>
	 * Rendered as a green {@code Verified} badge, deliberately matching what developers
	 * already recognise from the large forges.
	 *
	 * <p>
	 * <strong>What it does not mean.</strong> It is a statement about key custody, not
	 * about authorship. It does not establish that the student wrote the code, or wrote
	 * it unaided. No part of this product may present it as evidence of that, and any UI
	 * or report that does is a bug.
	 */
	VERIFIED,

	/** The commit carried no signature. */
	UNSIGNED,

	/** A signature was present but did not verify against the commit. */
	INVALID,

	/** The signature was valid but made with a key unknown to this instance. */
	UNKNOWN_KEY,

	/** The signing key is registered but was revoked, replaced or suspended. */
	KEY_REVOKED,

	/** The signing key is registered to a different student than the one who pushed. */
	WRONG_OWNER;

	/**
	 * Whether the signature is acceptable for admission.
	 * @return true only for {@link #VERIFIED}
	 */
	public boolean isAcceptable() {
		return this == VERIFIED;
	}

	/**
	 * A short label safe to show anywhere, including the public result page.
	 * @return {@code "Verified"} or {@code "Unverified"}
	 */
	public String badge() {
		return (this == VERIFIED) ? "Verified" : "Unverified";
	}

}
