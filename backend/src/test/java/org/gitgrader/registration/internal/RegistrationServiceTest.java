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

package org.gitgrader.registration.internal;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.audit.AuditService;
import org.gitgrader.audit.ClientAddressHasher;
import org.gitgrader.configuration.AppProperties;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentRegistry;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.identity.StudentView;
import org.gitgrader.registration.web.RegistrationRequest;
import org.gitgrader.security.RateLimiter;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests that a refused registration leaves a trace.
 *
 * <p>
 * The refusals are the whole point of {@code registration_attempts}: the table is indexed
 * by address and hour, carries a {@code RATE_LIMITED} outcome, and is described in the
 * schema as being there to investigate a flood. A registration that succeeds is already
 * recorded as a student.
 */
class RegistrationServiceTest {

	private static final String ADDRESS = "198.51.100.7";

	private RateLimiter rateLimiter;

	private CourseCatalog courseCatalog;

	private StudentDirectory studentDirectory;

	private StudentRegistry studentRegistry;

	private RegistrationAttemptLog attemptLog;

	private RegistrationService service;

	@BeforeEach
	void setUp() {
		this.rateLimiter = mock(RateLimiter.class);
		this.courseCatalog = mock(CourseCatalog.class);
		this.studentDirectory = mock(StudentDirectory.class);
		this.studentRegistry = mock(StudentRegistry.class);
		this.attemptLog = mock(RegistrationAttemptLog.class);
		ClientAddressHasher hasher = mock(ClientAddressHasher.class);
		when(hasher.hash(anyString())).thenReturn("hashed");

		when(this.rateLimiter.tryConsumeRegistrationGlobal()).thenReturn(true);
		when(this.rateLimiter.tryConsumeRegistrationPerIp(anyString())).thenReturn(true);
		when(this.courseCatalog.findCourses(eq(CourseStatus.ACTIVE), any(Pageable.class)))
			.thenReturn(new PageImpl<>(List.of(openCourse())));
		when(this.courseCatalog.findClasses(any(UUID.class))).thenReturn(List.of());
		when(this.studentDirectory.findByStudentNumber(anyString())).thenReturn(Optional.empty());

		this.service = new RegistrationService(properties(), this.rateLimiter, this.courseCatalog,
				this.studentDirectory, this.studentRegistry, mock(SshKeyRegistry.class), this.attemptLog, hasher,
				mock(AuditService.class), Clock.fixed(Instant.parse("2026-03-01T10:00:00Z"), ZoneOffset.UTC),
				mock(ApplicationEventPublisher.class));
	}

	@Test
	@DisplayName("records the attempt that hit the rate limit")
	void recordsARateLimitedAttempt() {
		when(this.rateLimiter.tryConsumeRegistrationPerIp(ADDRESS)).thenReturn(false);

		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.service.register(request(), ADDRESS));

		verify(this.attemptLog).record(any(), anyString(), eq("RATE_LIMITED"), any(), anyString(), anyString());
	}

	@Test
	@DisplayName("records the attempt that reused a student number")
	void recordsADuplicateStudentNumber() {
		when(this.studentDirectory.findByStudentNumber("12345")).thenReturn(Optional.of(existingStudent()));

		assertThatExceptionOfType(DuplicateRegistrationException.class)
			.isThrownBy(() -> this.service.register(request(), ADDRESS));

		verify(this.attemptLog).record(any(), anyString(), eq("DUPLICATE"), eq("Student number already registered"),
				anyString(), anyString());
	}

	@Test
	@DisplayName("records the attempt the unique index refused")
	void recordsADuplicateEmail() {
		when(this.studentRegistry.register(any())).thenThrow(new DataIntegrityViolationException("email"));

		assertThatExceptionOfType(DuplicateRegistrationException.class)
			.isThrownBy(() -> this.service.register(request(), ADDRESS));

		verify(this.attemptLog).record(any(), anyString(), eq("DUPLICATE"), eq("Email already registered"), anyString(),
				anyString());
	}

	@Test
	@DisplayName("writes the attempt outside the transaction that is about to roll back")
	void recordsOutsideTheFailingTransaction() throws NoSuchMethodException {
		// Every attempt worth investigating ends by throwing, and the throw rolls the
		// registration transaction back. Written on that transaction, as they were, the
		// rows went with it: the table could only ever hold the registrations that
		// succeeded, which is the one outcome nobody needs it for. Its own transaction
		// is what makes the record outlive the failure it describes.
		Method record = RegistrationAttemptLog.class.getDeclaredMethod("record", Instant.class, String.class,
				String.class, String.class, String.class, String.class);

		Transactional annotation = record.getAnnotation(Transactional.class);

		assertThat(annotation).isNotNull();
		assertThat(annotation.propagation()).isEqualTo(Propagation.REQUIRES_NEW);
	}

	@Test
	@DisplayName("does not record an attempt twice for one registration")
	void recordsEachAttemptOnce() {
		when(this.rateLimiter.tryConsumeRegistrationGlobal()).thenReturn(false);

		assertThatExceptionOfType(RateLimitExceededException.class)
			.isThrownBy(() -> this.service.register(request(), ADDRESS));

		verify(this.attemptLog, times(1)).record(any(), anyString(), anyString(), any(), anyString(), anyString());
	}

	private static AppProperties properties() {
		return new AppProperties("GitGrader", java.net.URI.create("http://localhost:8080"), "support@example.org",
				"Example", java.net.URI.create("https://example.org"), ZoneOffset.UTC, "/data",
				new AppProperties.Registration(true, false, 5),
				new AppProperties.ResultTokens(256, java.time.Duration.ofDays(180), 8));
	}

	private static CourseView openCourse() {
		return new CourseView(UUID.randomUUID(), "cs101", "Programming", null, null, null, null, "UTC",
				CourseStatus.ACTIVE, null, null, true);
	}

	private static StudentView existingStudent() {
		return new StudentView(UUID.randomUUID(), "12345", "Ada Lovelace", "ada@example.org",
				StudentStatus.SELF_REGISTERED, null, Instant.parse("2026-01-01T00:00:00Z"));
	}

	private static RegistrationRequest request() {
		return new RegistrationRequest("Ada", "Lovelace", "12345", "ada@example.org", "cs101", null,
				"ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAAIExample");
	}

}
