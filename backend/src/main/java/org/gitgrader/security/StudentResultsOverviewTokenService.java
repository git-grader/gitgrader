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
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

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

	/**
	 * Enough for two pushes of one repository colliding; more would be a defect, not a
	 * race.
	 */
	private static final int MAX_MINT_ATTEMPTS = 3;

	private final StudentResultsOverviewTokenRepository repository;

	private final AppProperties appProperties;

	private final Clock clock;

	/**
	 * Runs the insert on its own transaction so that losing the mint race cannot take the
	 * caller's transaction with it. PostgreSQL aborts an entire transaction when any
	 * statement fails, so a constraint violation caught inside the surrounding
	 * transaction leaves that transaction unable to run the re-read that recovers from
	 * it. The catch therefore has to happen outside a transaction that the failure
	 * poisoned.
	 */
	private final TransactionTemplate insertAttempt;

	public StudentResultsOverviewTokenService(StudentResultsOverviewTokenRepository repository,
			AppProperties appProperties, Clock clock, PlatformTransactionManager transactionManager) {
		this.repository = repository;
		this.appProperties = appProperties;
		this.clock = clock;
		this.insertAttempt = new TransactionTemplate(transactionManager);
		this.insertAttempt.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
	}

	/**
	 * Mints this push's token, retiring whatever the previous push was given.
	 *
	 * <p>
	 * The token is rotated rather than re-shown because it is no longer stored: only its
	 * hash is kept, and a hash cannot be turned back into the value the student needs.
	 * Each push therefore gets a new link and the previous one stops resolving once this
	 * push lands. A link a student is still holding keeps working until their next push.
	 * @param studentId the student the link is for
	 * @return the plain token, which the caller must hand to the student now
	 */
	public String issueForStudent(UUID studentId) {
		Instant now = Instant.now(this.clock);

		// Bounded because each retry needs a competing push to have inserted in between.
		// Two
		// pushes of one repository a moment apart is the realistic case and needs one
		// retry;
		// anything past that is not a race but a defect, and looping forever would hide
		// it.
		for (int attempt = 1; attempt <= MAX_MINT_ATTEMPTS; attempt++) {
			retireActive(studentId, now);
			try {
				return mint(studentId, now);
			}
			catch (DataIntegrityViolationException ex) {
				if (attempt == MAX_MINT_ATTEMPTS) {
					throw ex;
				}
				// Another push inserted between the retire above and this insert, so
				// uq_srot_one_active_per_student rejected this one. Retiring again picks
				// up that
				// row and the retry inserts in its place. The competitor's link is
				// revoked by the
				// retry, which is unavoidable once tokens rotate: there is one link per
				// student
				// and both pushes asked for a fresh one.
			}
		}
		throw new IllegalStateException("unreachable");
	}

	private void retireActive(UUID studentId, Instant now) {
		this.repository.findByStudentIdAndStatus(studentId, Status.ACTIVE).ifPresent((entity) -> {
			entity.revoke(now);
			this.repository.save(entity);
		});
	}

	private String mint(UUID studentId, Instant now) {
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

		StudentResultsOverviewToken entity = new StudentResultsOverviewToken(UUID.randomUUID(), studentId,
				TokenHash.of(plainToken), tokenPrefix, now, expiresAt, Status.ACTIVE);
		this.insertAttempt.executeWithoutResult((status) -> this.repository.saveAndFlush(entity));
		return plainToken;
	}

	/**
	 * Resolves a plain token to its student.
	 *
	 * <p>
	 * The token is hashed before the lookup, and there is no stored plaintext to compare
	 * it against. A revoked, expired or unknown token is indistinguishable from the
	 * outside.
	 * @param token the plain token from the request
	 * @return the student, or empty when the token cannot be used
	 */
	public Optional<UUID> resolve(String token) {
		Optional<StudentResultsOverviewToken> optionalEntity = this.repository.findByTokenHash(TokenHash.of(token));
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
