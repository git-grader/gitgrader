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

package org.gitgrader.audit;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Produces non-reversible, deployment-keyed hashes of client addresses. */
@Component
public class ClientAddressHasher {

	private static final Logger logger = LoggerFactory.getLogger(ClientAddressHasher.class);

	/** Shared: a per-call {@code SecureRandom} re-seeds from system entropy each time. */
	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private static final String ALGORITHM = "HmacSHA256";

	private static final int GENERATED_KEY_BYTES = 32;

	private static final int HASH_LENGTH = 32;

	private final byte[] key;

	/**
	 * Creates a hasher from configured audit properties.
	 * @param properties audit configuration
	 */
	public ClientAddressHasher(AuditProperties properties) {
		this.key = resolveKey(properties.ipHashKey());
	}

	/**
	 * Hashes a client address without retaining or exposing its raw value.
	 * @param clientAddress raw address to hash
	 * @return Base64url HMAC truncated to 32 characters
	 */
	public String hash(String clientAddress) {
		try {
			Mac mac = Mac.getInstance(ALGORITHM);
			mac.init(new SecretKeySpec(this.key, ALGORITHM));
			byte[] digest = mac.doFinal(clientAddress.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(digest).substring(0, HASH_LENGTH);
		}
		catch (GeneralSecurityException exception) {
			throw new IllegalStateException("HMAC-SHA256 is unavailable", exception);
		}
	}

	private static byte[] resolveKey(String configuredKey) {
		if (!configuredKey.isBlank()) {
			return configuredKey.getBytes(StandardCharsets.UTF_8);
		}
		byte[] generatedKey = new byte[GENERATED_KEY_BYTES];
		SECURE_RANDOM.nextBytes(generatedKey);
		logger.warn("audit.ip-hash-key is unset; client address hashes will not be comparable across restarts");
		return generatedKey;
	}

}
