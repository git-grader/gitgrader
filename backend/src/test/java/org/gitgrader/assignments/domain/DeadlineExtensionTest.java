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

package org.gitgrader.assignments.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link DeadlineExtension} entity.
 *
 * <p>
 * An extension moves the date a grade is judged against, so the grant carries a non-blank
 * reason, the granting instructor and the instant rather than a bare new deadline.
 * Revoking records its actor and instant instead of deleting the row, because a withdrawn
 * extension still has to remain legible to whoever reads the record of a deadline that
 * was in force at the time.
 */
class DeadlineExtensionTest {

	private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	@DisplayName("requires a reason and records the granting instructor and the instant")
	void grantRequiresReasonAndRecordsInstructorAndTimestamp() {
		assertThatThrownBy(() -> extension(" ")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("reason");

		assertThat(extension("medical").toView()).extracting("reason", "grantedBy", "grantedAt")
			.containsExactly("medical", "instructor", NOW);
	}

	@Test
	@DisplayName("revokes softly, recording the actor and the instant")
	void revocationIsSoftAndRecordsActorAndTime() {
		DeadlineExtension extension = extension("medical");
		extension.revoke("administrator", CLOCK);

		assertThat(extension.toView().revokedAt()).isEqualTo(NOW);
		assertThat(extension.toView().revokedBy()).isEqualTo("administrator");
	}

	private static DeadlineExtension extension(String reason) {
		return new DeadlineExtension(UUID.randomUUID(), UUID.randomUUID(), NOW.plusSeconds(3600), reason, "instructor",
				CLOCK);
	}

}
