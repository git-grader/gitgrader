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

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.configuration.AppProperties;
import org.gitgrader.configuration.AppProperties.ResultTokens;
import org.gitgrader.security.domain.ResultToken;
import org.gitgrader.security.internal.ResultTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ResultTokenService}, which mints the bearer token a result link
 * carries.
 *
 * <p>
 * The plaintext exists only in the response: the row keeps a SHA-256 hash and a short
 * prefix an operator can recognise a token by, so a stolen table is not a set of working
 * credentials. Expiry is read from the injected {@link Clock}, which is what makes a
 * lapsed token testable at all.
 *
 * <p>
 * Entropy is checked in both directions: a token must carry every configured bit, and the
 * configuration must refuse a size that would be silently rounded down to a byte boundary
 * or that falls below the floor.
 */
class ResultTokenServiceTest {

	private ResultTokenRepository repository;

	private AppProperties appProperties;

	private Clock clock;

	private ResultTokenService service;

	@BeforeEach
	void setUp() {
		repository = Mockito.mock(ResultTokenRepository.class);

		ResultTokens tokens = new ResultTokens(256, Duration.ofDays(180), 8);
		appProperties = Mockito.mock(AppProperties.class);
		when(appProperties.resultTokens()).thenReturn(tokens);

		clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"));
		service = new ResultTokenService(repository, appProperties, clock);
	}

	@Test
	@DisplayName("returns a unique token per issue and stores only its hash")
	void issueReturnsUniqueTokensAndStoresHash() {
		UUID submissionId = UUID.randomUUID();

		String token1 = service.issue(submissionId);
		String token2 = service.issue(submissionId);

		assertThat(token1).isNotEqualTo(token2);

		ArgumentCaptor<ResultToken> captor = ArgumentCaptor.forClass(ResultToken.class);
		verify(repository, Mockito.times(2)).save(captor.capture());

		ResultToken saved = captor.getAllValues().get(0);
		assertThat(saved.tokenHash()).isNotEqualTo(token1);
		assertThat(saved.tokenPrefix()).isEqualTo(token1.substring(0, 8));
	}

	@Test
	@DisplayName("resolves an expired token to nothing")
	void resolveExpiredTokenReturnsEmpty() {
		String token = service.issue(UUID.randomUUID());

		ResultToken entity = new ResultToken(UUID.randomUUID(), UUID.randomUUID(), hashOf(token), "prefix",
				Instant.now(clock), Instant.now(clock).minusSeconds(10));
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(entity));

		assertThat(service.resolve(token)).isEmpty();
	}

	@Test
	@DisplayName("resolves a valid token to its submission")
	void resolveValidTokenReturnsSubmission() {
		UUID submissionId = UUID.randomUUID();
		String token = service.issue(submissionId);

		ResultToken entity = new ResultToken(UUID.randomUUID(), submissionId, hashOf(token), "prefix",
				Instant.now(clock), Instant.now(clock).plusSeconds(600));
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(entity));

		assertThat(service.resolve(token)).contains(submissionId);
	}

	@Test
	@DisplayName("resolves an unknown token to nothing")
	void resolveUnknownTokenReturnsEmpty() {
		when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThat(service.resolve("not-a-token")).isEmpty();
	}

	@Test
	@DisplayName("resolves a revoked token to nothing")
	void resolveRevokedTokenReturnsEmpty() {
		String token = service.issue(UUID.randomUUID());

		ResultToken entity = new ResultToken(UUID.randomUUID(), UUID.randomUUID(), hashOf(token), "prefix",
				Instant.now(clock), Instant.now(clock).plusSeconds(10));
		entity.revoke("actor", Instant.now(clock));

		when(repository.findByTokenHash(any())).thenReturn(Optional.of(entity));

		assertThat(service.resolve(token)).isEmpty();
	}

	@Test
	@DisplayName("uses every configured bit of entropy in an issued token")
	void issueUsesEveryConfiguredBitOfEntropy() {
		when(appProperties.resultTokens()).thenReturn(new ResultTokens(128, Duration.ofDays(180), 8));

		String token = service.issue(UUID.randomUUID());

		// Base64 without padding: 16 bytes become 22 characters, and a token shorter
		// than that would mean fewer random bytes than the deployment asked for.
		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(128 / Byte.SIZE);
	}

	@Test
	@DisplayName("refuses entropy that would be silently rounded down")
	void refusesEntropyThatWouldBeSilentlyRoundedDown() {
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> new ResultTokens(130, Duration.ofDays(180), 8))
			.withMessageContaining("multiple of 8");
	}

	@Test
	@DisplayName("refuses entropy below the floor")
	void refusesEntropyBelowTheFloor() {
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> new ResultTokens(64, Duration.ofDays(180), 8))
			.withMessageContaining("at least 128");
	}

	private static String hashOf(String token) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return Base64.getUrlEncoder()
				.withoutPadding()
				.encodeToString(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException(ex);
		}
	}

}
