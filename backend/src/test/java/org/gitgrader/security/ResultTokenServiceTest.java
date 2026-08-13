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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
	void resolveExpiredTokenReturnsEmpty() {
		String token = service.issue(UUID.randomUUID());

		ResultToken entity = new ResultToken(UUID.randomUUID(), UUID.randomUUID(), hashOf(token), "prefix",
				Instant.now(clock), Instant.now(clock).minusSeconds(10));
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(entity));

		assertThat(service.resolve(token)).isEmpty();
	}

	@Test
	void resolveValidTokenReturnsSubmission() {
		UUID submissionId = UUID.randomUUID();
		String token = service.issue(submissionId);

		ResultToken entity = new ResultToken(UUID.randomUUID(), submissionId, hashOf(token), "prefix",
				Instant.now(clock), Instant.now(clock).plusSeconds(600));
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(entity));

		assertThat(service.resolve(token)).contains(submissionId);
	}

	@Test
	void resolveUnknownTokenReturnsEmpty() {
		when(repository.findByTokenHash(any())).thenReturn(Optional.empty());

		assertThat(service.resolve("not-a-token")).isEmpty();
	}

	@Test
	void resolveRevokedTokenReturnsEmpty() {
		String token = service.issue(UUID.randomUUID());

		ResultToken entity = new ResultToken(UUID.randomUUID(), UUID.randomUUID(), hashOf(token), "prefix",
				Instant.now(clock), Instant.now(clock).plusSeconds(10));
		entity.revoke("actor", Instant.now(clock));

		when(repository.findByTokenHash(any())).thenReturn(Optional.of(entity));

		assertThat(service.resolve(token)).isEmpty();
	}

	@Test
	void issueUsesEveryConfiguredBitOfEntropy() {
		when(appProperties.resultTokens()).thenReturn(new ResultTokens(128, Duration.ofDays(180), 8));

		String token = service.issue(UUID.randomUUID());

		// Base64 without padding: 16 bytes become 22 characters, and a token shorter
		// than that would mean fewer random bytes than the deployment asked for.
		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(128 / Byte.SIZE);
	}

	@Test
	void refusesEntropyThatWouldBeSilentlyRoundedDown() {
		assertThatExceptionOfType(IllegalArgumentException.class)
			.isThrownBy(() -> new ResultTokens(130, Duration.ofDays(180), 8))
			.withMessageContaining("multiple of 8");
	}

	@Test
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
