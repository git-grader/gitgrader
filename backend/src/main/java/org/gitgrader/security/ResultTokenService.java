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
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.configuration.AppProperties;
import org.gitgrader.security.domain.ResultToken;
import org.gitgrader.security.internal.ResultTokenRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Core security primitive for generating and validating result tokens.
 */
@Service
@Transactional
public class ResultTokenService {

	private static final Logger logger = LoggerFactory.getLogger(ResultTokenService.class);

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final ResultTokenRepository repository;

	private final AppProperties appProperties;

	private final Clock clock;

	public ResultTokenService(ResultTokenRepository repository, AppProperties appProperties, Clock clock) {
		this.repository = repository;
		this.appProperties = appProperties;
		this.clock = clock;
	}

	/**
	 * Issues a new token for the given submission.
	 * @param submissionId submission identifier
	 * @return the plain token string
	 */
	public String issue(UUID submissionId) {
		int entropyBits = this.appProperties.resultTokens().entropyBits();
		int bytesLength = entropyBits / Byte.SIZE;
		byte[] randomBytes = new byte[bytesLength];
		SECURE_RANDOM.nextBytes(randomBytes);

		String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
		String tokenHash = hash(plainToken);

		int prefixLength = Math.min(plainToken.length(), this.appProperties.resultTokens().prefixLength());
		String tokenPrefix = plainToken.substring(0, prefixLength);

		Instant now = Instant.now(this.clock);
		Instant expiresAt = null;
		if (this.appProperties.resultTokens().expires()) {
			expiresAt = now.plus(this.appProperties.resultTokens().timeToLive());
		}

		ResultToken entity = new ResultToken(UUID.randomUUID(), submissionId, tokenHash, tokenPrefix, now, expiresAt);
		this.repository.save(entity);

		return plainToken;
	}

	/**
	 * Resolves a token to a submission id, if valid.
	 *
	 * <p>
	 * What keeps a token unguessable is that only its SHA-256 is stored and the lookup is
	 * by that whole hash: a stolen database yields no usable link, and a near miss
	 * matches nothing. There is deliberately no comparison of the hash afterwards. The
	 * row was selected by equality on that exact value, so such a check can never fail,
	 * and calling it a constant-time comparison described a protection that was not
	 * there.
	 * @param token plain token
	 * @return submission id
	 */
	public Optional<UUID> resolve(String token) {
		String tokenHash = hash(token);
		Optional<ResultToken> optionalEntity = this.repository.findByTokenHash(tokenHash);

		if (optionalEntity.isEmpty()) {
			return Optional.empty();
		}

		ResultToken entity = optionalEntity.get();

		Instant now = Instant.now(this.clock);
		if (!entity.isValid(now)) {
			return Optional.empty();
		}

		entity.recordAccess(now);
		this.repository.save(entity);

		return Optional.of(entity.submissionId());
	}

	/**
	 * Revokes a token.
	 * @param tokenId token id
	 * @param actor who revoked it
	 */
	public void revoke(UUID tokenId, String actor) {
		this.repository.findById(tokenId).ifPresent((entity) -> {
			entity.revoke(actor, Instant.now(this.clock));
			this.repository.save(entity);
		});
	}

	private String hash(String value) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 missing", ex);
		}
	}

}
