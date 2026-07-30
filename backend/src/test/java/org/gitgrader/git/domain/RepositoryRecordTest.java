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

package org.gitgrader.git.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.gitgrader.git.RepositoryStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the {@link RepositoryRecord} lifecycle.
 */
class RepositoryRecordTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-01T09:00:00Z"), ZoneOffset.UTC);

	@Test
	@DisplayName("a new repository is pending and refuses pushes until it is provisioned")
	void startsPending() {
		RepositoryRecord record = newRecord();

		assertThat(record.status()).isEqualTo(RepositoryStatus.PENDING);
		// A row can exist before the bare repository does. Accepting a push in that
		// window
		// would write objects into a directory that is not a repository yet.
		assertThat(record.acceptsPushes()).isFalse();
		assertThat(record.provisionedAt()).isNull();
	}

	@Test
	@DisplayName("provisioning pins the template version the repository was seeded from")
	void provisioningPinsTemplateVersion() {
		RepositoryRecord record = newRecord();
		UUID templateVersion = UUID.randomUUID();

		record.markProvisioned(templateVersion, CLOCK);

		// Pinned rather than looked up later: publishing a newer template version must
		// not
		// retroactively change what an already-provisioned repository was created from.
		assertThat(record.templateVersionId()).isEqualTo(templateVersion);
		assertThat(record.status()).isEqualTo(RepositoryStatus.READY);
		assertThat(record.acceptsPushes()).isTrue();
		assertThat(record.provisionedAt()).isEqualTo(Instant.parse("2026-04-01T09:00:00Z"));
	}

	@Test
	@DisplayName("counts pushes and remembers the most recent one")
	void countsPushes() {
		RepositoryRecord record = newRecord();
		record.markProvisioned(null, CLOCK);
		assertThat(record.pushCount()).isZero();

		record.recordPush(CLOCK);
		record.recordPush(CLOCK);

		assertThat(record.pushCount()).isEqualTo(2);
		assertThat(record.lastPushAt()).isEqualTo(Instant.parse("2026-04-01T09:00:00Z"));
	}

	@Test
	@DisplayName("only a READY repository accepts pushes")
	void onlyReadyAcceptsPushes() {
		assertThat(RepositoryStatus.READY.acceptsPushes()).isTrue();
		assertThat(RepositoryStatus.PENDING.acceptsPushes()).isFalse();
		assertThat(RepositoryStatus.LOCKED.acceptsPushes()).isFalse();
		assertThat(RepositoryStatus.ARCHIVED.acceptsPushes()).isFalse();
	}

	@Test
	@DisplayName("keeps the identifiers the transport authorizes against")
	void keepsAuthorizationIdentifiers() {
		UUID assignment = UUID.randomUUID();
		UUID student = UUID.randomUUID();

		RepositoryRecord record = new RepositoryRecord(assignment, student, "course-a/assignment-01/12345", CLOCK);

		assertThat(record.assignmentId()).isEqualTo(assignment);
		assertThat(record.studentId()).isEqualTo(student);
		assertThat(record.repositoryPath()).isEqualTo("course-a/assignment-01/12345");
		assertThat(record.id()).isNotNull();
	}

	private static RepositoryRecord newRecord() {
		return new RepositoryRecord(UUID.randomUUID(), UUID.randomUUID(), "course-a/assignment-01/12345", CLOCK);
	}

}
