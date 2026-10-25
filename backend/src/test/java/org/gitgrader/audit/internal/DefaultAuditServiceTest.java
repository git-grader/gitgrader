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

package org.gitgrader.audit.internal;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditProperties;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.domain.AuditEventEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests that {@link DefaultAuditService} never lets a failure to write the trail become a
 * failure of the operation it was recording, and that retention purging honours the
 * configured period.
 *
 * <p>
 * Recording is a bystander: an instructor changing a course, or a student being graded,
 * must not fail because the audit table was unreachable. The failure has to be logged
 * instead, which is the one thing this class asserts - that the exception stops here.
 */
class DefaultAuditServiceTest {

	private static final Instant NOW = Instant.parse("2026-03-01T10:15:30Z");

	private final AuditEventRepository repository = mock(AuditEventRepository.class);

	private final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	@DisplayName("never propagates a persistence failure to the business caller")
	void persistenceFailureNeverPropagatesToBusinessCaller() {
		when(this.repository.save(any(AuditEventEntity.class)))
			.thenThrow(new IllegalStateException("database unavailable"));
		DefaultAuditService service = new DefaultAuditService(this.repository, this.clock,
				properties(Duration.ofDays(365)));

		assertThatCode(() -> service.record(AuditRecord.of(AuditEventType.COURSE_CHANGED).build()))
			.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("purges events older than the retention period")
	void purgesEventsOlderThanRetention() {
		AuditEventRepository repository = mock(AuditEventRepository.class);
		when(repository.deleteOlderThan(NOW.minus(Duration.ofDays(30)))).thenReturn(7);
		DefaultAuditService service = new DefaultAuditService(repository, this.clock, properties(Duration.ofDays(30)));

		int removed = service.purgeExpiredEvents();

		assertThat(removed).isEqualTo(7);
		verify(repository).deleteOlderThan(NOW.minus(Duration.ofDays(30)));
	}

	@Test
	@DisplayName("skips the purge when retention is not positive")
	void skipsPurgeWhenRetentionIsNotPositive() {
		AuditEventRepository repository = mock(AuditEventRepository.class);
		DefaultAuditService service = new DefaultAuditService(repository, this.clock, properties(Duration.ZERO));

		int removed = service.purgeExpiredEvents();

		assertThat(removed).isZero();
		verifyNoInteractions(repository);
	}

	@Test
	@DisplayName("a purge failure does not propagate to the scheduler")
	void purgeFailureDoesNotPropagate() {
		AuditEventRepository repository = mock(AuditEventRepository.class);
		when(repository.deleteOlderThan(any())).thenThrow(new IllegalStateException("database unavailable"));
		DefaultAuditService service = new DefaultAuditService(repository, this.clock, properties(Duration.ofDays(365)));
		AuditRetentionScheduler scheduler = new AuditRetentionScheduler(service);

		assertThatCode(() -> scheduler.purgeExpiredAuditEvents()).doesNotThrowAnyException();
		verify(repository).deleteOlderThan(any());
	}

	private static AuditProperties properties(Duration retention) {
		return new AuditProperties("", retention);
	}

}
