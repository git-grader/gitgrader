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
import org.mockito.InOrder;
import org.mockito.Mockito;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudentResultsOverviewTokenServiceTest {

	private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

	private StudentResultsOverviewTokenRepository repository;

	private AppProperties appProperties;

	private Clock clock;

	private PlatformTransactionManager transactionManager;

	private StudentResultsOverviewTokenService service;

	@BeforeEach
	void setUp() {
		this.repository = Mockito.mock(StudentResultsOverviewTokenRepository.class);
		this.appProperties = Mockito.mock(AppProperties.class);
		when(this.appProperties.resultTokens()).thenReturn(new ResultTokens(256, Duration.ofDays(180), 8));
		this.clock = Clock.fixed(Instant.parse("2026-01-01T10:00:00Z"), ZoneId.of("UTC"));
		// Every mint attempt runs on its own transaction, so a rejected insert can be
		// rolled back whole instead of poisoning the caller's transaction - which is what
		// makes the lost-race recovery possible, and what keeps the retire and the insert
		// on one connection so the insert can see the retire. A transaction manager that
		// simply hands back a status is enough: these tests assert on repository calls.
		this.transactionManager = Mockito.mock(PlatformTransactionManager.class);
		when(this.transactionManager.getTransaction(any())).thenReturn(new SimpleTransactionStatus());
		this.service = new StudentResultsOverviewTokenService(this.repository, this.appProperties, this.clock,
				this.transactionManager);
	}

	@Test
	@DisplayName("issues an active token, storing only its hash")
	void issuesNewToken() {
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.empty());

		String token = this.service.issueForStudent(STUDENT);

		ArgumentCaptor<StudentResultsOverviewToken> captor = ArgumentCaptor.forClass(StudentResultsOverviewToken.class);
		verify(this.repository).saveAndFlush(captor.capture());
		StudentResultsOverviewToken saved = captor.getValue();
		assertThat(saved.studentId()).isEqualTo(STUDENT);
		// The stored value must not be the usable one: a backup of this table must not be
		// a
		// working credential.
		assertThat(saved.tokenHash()).isNotEqualTo(token).isEqualTo(TokenHash.of(token));
		assertThat(saved.tokenPrefix()).isEqualTo(token.substring(0, 8));
		assertThat(saved.status()).isEqualTo(Status.ACTIVE);
		assertThat(saved.expiresAt()).isEqualTo(Instant.now(this.clock).plus(Duration.ofDays(180)));
	}

	@Test
	@DisplayName("rotates the token on every push and withdraws the previous link")
	void rotatesTheTokenOnEveryPush() {
		StudentResultsOverviewToken previous = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				TokenHash.of("previous-token"), "previous-", Instant.now(this.clock).minusSeconds(60),
				Instant.now(this.clock).plusSeconds(600), Status.ACTIVE);
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.of(previous),
				Optional.empty());

		String first = this.service.issueForStudent(STUDENT);
		String second = this.service.issueForStudent(STUDENT);

		// The hash cannot be reversed, so the previous link is withdrawn rather than
		// re-shown.
		// That is the trade that removes the plaintext from the table.
		assertThat(previous.status()).isEqualTo(Status.REVOKED);
		assertThat(first).isNotEqualTo("previous-token");
		assertThat(second).isNotEqualTo(first);
		verify(this.repository, Mockito.times(2)).saveAndFlush(any(StudentResultsOverviewToken.class));
	}

	@Test
	@DisplayName("retires an expired token rather than leaving it active")
	void replacesExpiredToken() {
		StudentResultsOverviewToken expired = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				TokenHash.of("expired-token"), "expired-", Instant.now(this.clock),
				Instant.now(this.clock).minusSeconds(10), Status.ACTIVE);
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.of(expired));

		String token = this.service.issueForStudent(STUDENT);

		// Rotating withdraws whatever was active, expired or not, so the slot is
		// genuinely free.
		assertThat(expired.status()).isIn(Status.REVOKED, Status.EXPIRED);
		assertThat(expired.isActive()).isFalse();
		assertThat(token).isNotEqualTo("expired-token");
		verify(this.repository).save(expired);
		verify(this.repository).saveAndFlush(any(StudentResultsOverviewToken.class));
	}

	@Test
	@DisplayName("retries once when a competing push wins the race, rather than failing the push")
	void recoversFromLostMintRace() {
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.empty());
		when(this.repository.saveAndFlush(any(StudentResultsOverviewToken.class)))
			.thenThrow(new DataIntegrityViolationException("uq_srot_one_active_per_student"))
			.thenAnswer((invocation) -> invocation.getArgument(0));

		String token = this.service.issueForStudent(STUDENT);

		// The competing push inserted between the retire and this insert. Retiring again
		// and
		// inserting once more is what keeps one link per student without a 500.
		assertThat(token).isNotBlank();
		verify(this.repository, Mockito.times(2)).saveAndFlush(any(StudentResultsOverviewToken.class));
	}

	@Test
	@DisplayName("gives up after bounded attempts instead of looping on a persistent constraint failure")
	void givesUpAfterBoundedAttempts() {
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.empty());
		when(this.repository.saveAndFlush(any(StudentResultsOverviewToken.class)))
			.thenThrow(new DataIntegrityViolationException("uq_srot_one_active_per_student"));

		assertThatExceptionOfType(DataIntegrityViolationException.class)
			.isThrownBy(() -> this.service.issueForStudent(STUDENT));
		verify(this.repository, Mockito.times(3)).saveAndFlush(any(StudentResultsOverviewToken.class));
	}

	@Test
	@DisplayName("rolls the rejected attempt back whole, so the retry re-reads from a clean slate")
	void rollsBackTheRejectedAttempt() {
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.empty());
		when(this.repository.saveAndFlush(any(StudentResultsOverviewToken.class)))
			.thenThrow(new DataIntegrityViolationException("uq_srot_one_active_per_student"))
			.thenAnswer((invocation) -> invocation.getArgument(0));

		this.service.issueForStudent(STUDENT);

		// PostgreSQL refuses every later statement in a transaction that failed, so the
		// retry can only re-read if the failed attempt was rolled back first. One
		// rollback for the rejected attempt and one commit for the one that won.
		verify(this.transactionManager, Mockito.times(1)).rollback(any());
		verify(this.transactionManager, Mockito.times(1)).commit(any());
	}

	@Test
	@DisplayName("flushes the retire before minting, so the insert never meets the row it replaces")
	void flushesTheRetireBeforeMinting() {
		StudentResultsOverviewToken previous = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				TokenHash.of("previous-token"), "previous-", Instant.now(this.clock).minusSeconds(60),
				Instant.now(this.clock).plusSeconds(600), Status.ACTIVE);
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.of(previous));

		this.service.issueForStudent(STUDENT);

		// Hibernate runs a flush's inserts ahead of its updates, so without an explicit
		// flush the insert would check uq_srot_one_active_per_student against the very
		// row this attempt is retiring and be rejected without any race at all.
		ArgumentCaptor<StudentResultsOverviewToken> captor = ArgumentCaptor.forClass(StudentResultsOverviewToken.class);
		InOrder inOrder = Mockito.inOrder(this.repository);
		inOrder.verify(this.repository).save(captor.capture());
		inOrder.verify(this.repository).flush();
		inOrder.verify(this.repository).saveAndFlush(any(StudentResultsOverviewToken.class));
		assertThat(captor.getValue().status()).isEqualTo(Status.REVOKED);
	}

	@Test
	@DisplayName("omits the expiry instant when the deployment disables result expiry")
	void honoursExpiryDisabled() {
		when(this.appProperties.resultTokens()).thenReturn(new ResultTokens(256, Duration.ZERO, 8));
		when(this.repository.findByStudentIdAndStatus(STUDENT, Status.ACTIVE)).thenReturn(Optional.empty());

		this.service.issueForStudent(STUDENT);

		ArgumentCaptor<StudentResultsOverviewToken> captor = ArgumentCaptor.forClass(StudentResultsOverviewToken.class);
		verify(this.repository).saveAndFlush(captor.capture());
		assertThat(captor.getValue().expiresAt()).isNull();
	}

	@Test
	@DisplayName("resolve returns the student and records the access")
	void resolveRecordsAccess() {
		StudentResultsOverviewToken existing = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				TokenHash.of("live-token"), "live-tok", Instant.now(this.clock),
				Instant.now(this.clock).plusSeconds(600), Status.ACTIVE);
		when(this.repository.findByTokenHash(TokenHash.of("live-token"))).thenReturn(Optional.of(existing));

		assertThat(this.service.resolve("live-token")).contains(STUDENT);
		assertThat(existing.accessCount()).isEqualTo(1);
		assertThat(existing.lastUsedAt()).isEqualTo(Instant.now(this.clock));
		verify(this.repository).save(existing);
	}

	@Test
	@DisplayName("resolve treats an unknown token and a revoked one identically")
	void resolveRejectsUnusableTokens() {
		when(this.repository.findByTokenHash(TokenHash.of("missing"))).thenReturn(Optional.empty());
		assertThat(this.service.resolve("missing")).isEmpty();

		StudentResultsOverviewToken revoked = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				TokenHash.of("revoked-token"), "revoked-", Instant.now(this.clock),
				Instant.now(this.clock).plusSeconds(600), Status.REVOKED);
		when(this.repository.findByTokenHash(TokenHash.of("revoked-token"))).thenReturn(Optional.of(revoked));
		assertThat(this.service.resolve("revoked-token")).isEmpty();
	}

	@Test
	@DisplayName("resolve marks an expired-but-active token and refuses it")
	void resolveMarksExpired() {
		StudentResultsOverviewToken expired = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				TokenHash.of("stale-token"), "stale-to", Instant.now(this.clock),
				Instant.now(this.clock).minusSeconds(10), Status.ACTIVE);
		when(this.repository.findByTokenHash(TokenHash.of("stale-token"))).thenReturn(Optional.of(expired));

		assertThat(this.service.resolve("stale-token")).isEmpty();
		assertThat(expired.status()).isEqualTo(Status.EXPIRED);
		verify(this.repository).save(expired);
	}

	@Test
	@DisplayName("revoke withdraws the student's active token")
	void revokeWithdrawsActiveToken() {
		StudentResultsOverviewToken existing = new StudentResultsOverviewToken(UUID.randomUUID(), STUDENT,
				TokenHash.of("live-token"), "live-tok", Instant.now(this.clock),
				Instant.now(this.clock).plusSeconds(600), Status.ACTIVE);
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
