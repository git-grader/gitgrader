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
 * Helpers for presenting OpenSSH key fingerprints.
 */
public final class Fingerprints {

	/** Prefix OpenSSH puts in front of a SHA-256 fingerprint. */
	public static final String SHA256_PREFIX = "SHA256:";

	/** Base64 characters kept after the prefix when abbreviating for display. */
	public static final int DISPLAY_CHARS = 12;

	private Fingerprints() {
	}

	/**
	 * Shortens a fingerprint so it fits in a dense table.
	 *
	 * <p>
	 * <strong>Display only.</strong> An abbreviated fingerprint is not unique enough to
	 * identify a key, so it must never be used for a lookup, a comparison or an
	 * authorization decision. It exists purely so a table column does not wrap.
	 * @param fingerprint the full fingerprint
	 * @return the abbreviated form, or the input when it is already short enough
	 */
	public static String abbreviate(String fingerprint) {
		int cut = SHA256_PREFIX.length() + DISPLAY_CHARS;
		return (fingerprint.length() <= cut) ? fingerprint : fingerprint.substring(0, cut) + "...";
	}

}
