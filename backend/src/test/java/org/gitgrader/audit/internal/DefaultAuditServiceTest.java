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
import java.time.Instant;
import java.time.ZoneOffset;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.domain.AuditEventEntity;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAuditServiceTest {

	@Test
	void persistenceFailureNeverPropagatesToBusinessCaller() {
		AuditEventRepository repository = mock(AuditEventRepository.class);
		when(repository.save(any(AuditEventEntity.class))).thenThrow(new IllegalStateException("database unavailable"));
		Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);
		DefaultAuditService service = new DefaultAuditService(repository, clock);

		assertThatCode(() -> service.record(AuditRecord.of(AuditEventType.COURSE_CHANGED).build()))
			.doesNotThrowAnyException();
	}

}
