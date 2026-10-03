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
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.configuration.AppProperties;
import org.gitgrader.configuration.AppProperties.ResultTokens;
import org.gitgrader.security.domain.StudentResultsOverviewToken;
import org.gitgrader.security.domain.StudentResultsOverviewToken.Status;
import org.gitgrader.security.internal.StudentResultsOverviewTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentResultsOverviewTokenServiceTest {

	private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

	private StudentResultsOverviewTokenRepository repository;

	private AppProperties appProperties;

	private Clock clock;

	private StudentResultsOverviewTokenService service;

	@BeforeEach
	void setUp() {
		this.repository = Mockito.mock(StudentResultsOverviewTokenRepository.class);
		this.appProperties = Mockito.mock(AppProperties.class);
		when(this.appProperties.resultTokens()).thenReturn(new ResultTokens(256, Duration.ofDays(180), 8));
		this.clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"));
		this.service = new StudentResultsOverviewTokenService(this.repository, this.appProperties, this.clock);
	}

	@Test
	@DisplayName("issues a new active token and keeps the plain value so the link is stable")
	void issuesNewToken() {
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.empty());

		String token = this.service.issueForStudent(STUDENT);

		ArgumentCaptor<StudentResultsOverviewToken> captor = ArgumentCaptor.forClass(StudentResultsOverviewToken.class);
		verify(this.repository).save(captor.capture());
		StudentResultsOverviewToken saved = captor.getValue();
		assertThat(saved.studentId()).isEqualTo(STUDENT);
		assertThat(saved.tokenValue()).isEqualTo(token);
		assertThat(saved.tokenPrefix()).isEqualTo(token.substring(0, 8));
		assertThat(saved.status()).isEqualTo(Status.ACTIVE);
		assertThat(saved.expiresAt()).isEqualTo(Instant.now(this.clock).plus(Duration.ofDays(180)));
	}

	@Test
	@DisplayName("reuses the still-active token instead of minting a new link on every push")
	void reusesActiveToken() {
		StudentResultsOverviewToken existing = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				"stable-token-value", "stable-t", Instant.now(this.clock), Instant.now(this.clock).plusSeconds(600),
				Status.ACTIVE);
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.of(existing));

		String token = this.service.issueForStudent(STUDENT);

		assertThat(token).isEqualTo("stable-token-value");
		verify(this.repository, never()).save(any(StudentResultsOverviewToken.class));
	}

	@Test
	@DisplayName("retires an expired token before issuing its replacement")
	void replacesExpiredToken() {
		StudentResultsOverviewToken expired = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				"expired-token", "expired-", Instant.now(this.clock), Instant.now(this.clock).minusSeconds(10),
				Status.ACTIVE);
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.of(expired));

		String token = this.service.issueForStudent(STUDENT);

		assertThat(expired.status()).isEqualTo(Status.EXPIRED);
		assertThat(token).isNotEqualTo("expired-token");
		verify(this.repository, Mockito.times(2)).save(any(StudentResultsOverviewToken.class));
	}

	@Test
	@DisplayName("omits the expiry instant when the deployment disables result expiry")
	void honoursExpiryDisabled() {
		when(this.appProperties.resultTokens()).thenReturn(new ResultTokens(256, Duration.ZERO, 8));
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.empty());

		this.service.issueForStudent(STUDENT);

		ArgumentCaptor<StudentResultsOverviewToken> captor = ArgumentCaptor.forClass(StudentResultsOverviewToken.class);
		verify(this.repository).save(captor.capture());
		assertThat(captor.getValue().expiresAt()).isNull();
	}

	@Test
	@DisplayName("resolve returns the student and records the access")
	void resolveRecordsAccess() {
		StudentResultsOverviewToken existing = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT, "live-token",
				"live-tok", Instant.now(this.clock), Instant.now(this.clock).plusSeconds(600), Status.ACTIVE);
		when(this.repository.findByTokenValue("live-token")).thenReturn(Optional.of(existing));

		assertThat(this.service.resolve("live-token")).contains(STUDENT);
		assertThat(existing.accessCount()).isEqualTo(1);
		assertThat(existing.lastUsedAt()).isEqualTo(Instant.now(this.clock));
		verify(this.repository).save(existing);
	}

	@Test
	@DisplayName("resolve treats an unknown token and a revoked one identically")
	void resolveRejectsUnusableTokens() {
		when(this.repository.findByTokenValue("missing")).thenReturn(Optional.empty());
		assertThat(this.service.resolve("missing")).isEmpty();

		StudentResultsOverviewToken revoked = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				"revoked-token", "revoked-", Instant.now(this.clock), Instant.now(this.clock).plusSeconds(600),
				Status.REVOKED);
		when(this.repository.findByTokenValue("revoked-token")).thenReturn(Optional.of(revoked));
		assertThat(this.service.resolve("revoked-token")).isEmpty();
	}

	@Test
	@DisplayName("resolve marks an expired-but-active token and refuses it")
	void resolveMarksExpired() {
		StudentResultsOverviewToken expired = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT, "stale-token",
				"stale-to", Instant.now(this.clock), Instant.now(this.clock).minusSeconds(10), Status.ACTIVE);
		when(this.repository.findByTokenValue("stale-token")).thenReturn(Optional.of(expired));

		assertThat(this.service.resolve("stale-token")).isEmpty();
		assertThat(expired.status()).isEqualTo(Status.EXPIRED);
		verify(this.repository).save(expired);
	}

	@Test
	@DisplayName("revoke withdraws the student's active token")
	void revokeWithdrawsActiveToken() {
		StudentResultsOverviewToken existing = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT, "live-token",
				"live-tok", Instant.now(this.clock), Instant.now(this.clock).plusSeconds(600), Status.ACTIVE);
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.of(existing));

		this.service.revoke(STUDENT);

		assertThat(existing.status()).isEqualTo(Status.REVOKED);
		verify(this.repository).save(existing);
	}

	@Test
	@DisplayName("issues every configured bit of entropy")
	void issuesConfiguredEntropy() {
		when(this.appProperties.resultTokens()).thenReturn(new ResultTokens(128, Duration.ofDays(180), 8));
		when(this.repository.findByStudentIdAndStatus(eq(STUDENT), eq(Status.ACTIVE))).thenReturn(Optional.empty());

		String token = this.service.issueForStudent(STUDENT);

		assertThat(Base64.getUrlDecoder().decode(token)).hasSize(128 / Byte.SIZE);
	}

}
