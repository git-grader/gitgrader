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
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
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

		ResultToken entity = new ResultToken(UUID.randomUUID(), UUID.randomUUID(), "hash", "prefix", Instant.now(clock),
				Instant.now(clock).minusSeconds(10));
		when(repository.findByTokenHash(any())).thenReturn(Optional.of(entity));

		assertThat(service.resolve(token)).isEmpty();
	}

	@Test
	void resolveRevokedTokenReturnsEmpty() {
		String token = service.issue(UUID.randomUUID());

		ResultToken entity = new ResultToken(UUID.randomUUID(), UUID.randomUUID(), "hash", "prefix", Instant.now(clock),
				Instant.now(clock).plusSeconds(10));
		entity.revoke("actor", Instant.now(clock));

		when(repository.findByTokenHash(any())).thenReturn(Optional.of(entity));

		assertThat(service.resolve(token)).isEmpty();
	}

}
