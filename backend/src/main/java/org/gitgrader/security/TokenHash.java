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

package org.gitgrader.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

/**
 * The single hash used for every token this application issues.
 *
 * <p>
 * Two tables store token hashes, and a migration has to reproduce this function in SQL to
 * backfill values that were stored in plaintext. Any change here silently invalidates
 * every hash already in the database, because a lookup computes this function and
 * compares it to what was written when the token was issued. The SQL form that must stay
 * in step is:
 *
 * <pre>
 * translate(rtrim(encode(sha256(convert_to(value, 'UTF8')), 'base64'), '='), '+/', '-_')
 * </pre>
 *
 * <p>
 * That is Base64URL without padding: {@code encode} produces the standard alphabet with
 * {@code =} padding, so the translate and rtrim are what turn it into the Java encoding.
 */
public final class TokenHash {

	private TokenHash() {
	}

	/**
	 * Hashes a token for storage and lookup.
	 * @param value the plain token
	 * @return the Base64URL-unpadded SHA-256 digest
	 */
	public static String of(String value) {
		try {
			byte[] hash = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 missing", ex);
		}
	}

}
