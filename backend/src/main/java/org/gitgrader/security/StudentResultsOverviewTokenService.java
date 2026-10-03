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

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.configuration.AppProperties;
import org.gitgrader.security.domain.StudentResultsOverviewToken;
import org.gitgrader.security.domain.StudentResultsOverviewToken.Status;
import org.gitgrader.security.internal.StudentResultsOverviewTokenRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Issues and resolves the long-lived, student-scoped results overview token.
 *
 * <p>
 * Unlike a per-submission result token, this one is re-shown on every push, so the plain
 * value is kept to hand back the same stable link instead of rotating it. A revoked or
 * expired token is treated exactly like an unknown one.
 */
@Service
@Transactional
public class StudentResultsOverviewTokenService {

	private static final SecureRandom SECURE_RANDOM = new SecureRandom();

	private final StudentResultsOverviewTokenRepository repository;

	private final AppProperties appProperties;

	private final Clock clock;

	public StudentResultsOverviewTokenService(StudentResultsOverviewTokenRepository repository,
			AppProperties appProperties, Clock clock) {
		this.repository = repository;
		this.appProperties = appProperties;
		this.clock = clock;
	}

	/**
	 * Returns an existing active unexpired token for the student, or creates a new one.
	 */
	public String issueForStudent(UUID studentId) {
		Instant now = Instant.now(this.clock);
		Optional<StudentResultsOverviewToken> existing = this.repository.findByStudentIdAndStatus(studentId,
				Status.ACTIVE);
		if (existing.isPresent()) {
			StudentResultsOverviewToken token = existing.get();
			if (token.isExpired(now)) {
				token.markExpired(now);
				this.repository.save(token);
				return createNew(studentId, now);
			}
			return token.tokenValue();
		}
		return createNew(studentId, now);
	}

	private String createNew(UUID studentId, Instant now) {
		int entropyBits = this.appProperties.resultTokens().entropyBits();
		int bytesLength = entropyBits / Byte.SIZE;
		byte[] randomBytes = new byte[bytesLength];
		SECURE_RANDOM.nextBytes(randomBytes);

		String plainToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);

		int prefixLength = Math.min(plainToken.length(), this.appProperties.resultTokens().prefixLength());
		String tokenPrefix = plainToken.substring(0, prefixLength);

		Instant expiresAt = null;
		if (this.appProperties.resultTokens().expires()) {
			expiresAt = now.plus(this.appProperties.resultTokens().timeToLive());
		}

		StudentResultsOverviewToken entity = new StudentResultsOverviewToken(UUID.randomUUID(), studentId, plainToken,
				tokenPrefix, now, expiresAt, Status.ACTIVE);
		this.repository.save(entity);
		return plainToken;
	}

	public Optional<UUID> resolve(String token) {
		Optional<StudentResultsOverviewToken> optionalEntity = this.repository.findByTokenValue(token);
		if (optionalEntity.isEmpty()) {
			return Optional.empty();
		}
		StudentResultsOverviewToken entity = optionalEntity.get();
		Instant now = Instant.now(this.clock);
		if (!entity.isActive() || entity.isExpired(now)) {
			if (entity.isActive() && entity.isExpired(now)) {
				entity.markExpired(now);
				this.repository.save(entity);
			}
			return Optional.empty();
		}
		entity.recordAccess(now);
		this.repository.save(entity);
		return Optional.of(entity.studentId());
	}

	public void revoke(UUID studentId) {
		this.repository.findByStudentIdAndStatus(studentId, Status.ACTIVE).ifPresent((entity) -> {
			entity.revoke(Instant.now(this.clock));
			this.repository.save(entity);
		});
	}

}
