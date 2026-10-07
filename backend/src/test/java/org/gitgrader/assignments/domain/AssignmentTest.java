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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.gitgrader.assignments.AdmissionDecision;
import org.gitgrader.assignments.AssignmentDefinition;
import org.gitgrader.assignments.AssignmentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link Assignment} admission decision, and for the guard that decides
 * when one may be published.
 *
 * <p>
 * Every boundary here is a boundary a student's push lands on: the deadline holds to the
 * nanosecond, a late push is accepted and flagged rather than dropped when the assignment
 * allows it, and each lifecycle state yields its own distinct outcome so a caller is told
 * why it was refused. The decision rests on the server's receive time, and the signature
 * is asserted as well as the behaviour so a parameter carrying the client's own commit
 * date cannot be added back unnoticed.
 *
 * <p>
 * Publication is guarded from the other side: an assignment cannot open without the
 * material it needs, and a schedule whose due date precedes its opening is refused rather
 * than published as an assignment no push could ever satisfy.
 */
class AssignmentTest {

	private static final Instant OPENS = Instant.parse("2026-03-01T10:00:00Z");

	private static final Instant DUE = Instant.parse("2026-03-01T12:00:00Z");

	private static final Clock CLOCK = Clock.fixed(OPENS, ZoneOffset.UTC);

	@Test
	@DisplayName("accepts a push exactly on the deadline and rejects one nanosecond later")
	void exactDeadlineIsAcceptedAndOneNanosecondLaterIsRejected() {
		Assignment assignment = assignment(AssignmentStatus.OPEN, false);

		assertThat(assignment.canAccept(DUE, DUE).outcome()).isEqualTo(AdmissionDecision.Outcome.ACCEPTED);
		assertThat(assignment.canAccept(DUE.plusNanos(1), DUE).outcome())
			.isEqualTo(AdmissionDecision.Outcome.PAST_DEADLINE);
	}

	@Test
	@DisplayName("accepts and flags a late push when late work is allowed")
	void latePushIsAcceptedAndFlaggedWhenLateWorkIsAllowed() {
		AdmissionDecision decision = assignment(AssignmentStatus.OPEN, true).canAccept(DUE.plusSeconds(1), DUE);

		assertThat(decision.outcome()).isEqualTo(AdmissionDecision.Outcome.ACCEPTED);
		assertThat(decision.late()).isTrue();
	}

	@Test
	@DisplayName("decides on the lifecycle state and the opening time separately")
	void statesAndOpeningTimeProduceDistinctDecisions() {
		assertThat(assignment(AssignmentStatus.DRAFT, false).canAccept(OPENS, DUE).outcome())
			.isEqualTo(AdmissionDecision.Outcome.ASSIGNMENT_DRAFT);
		assertThat(assignment(AssignmentStatus.CLOSED, false).canAccept(OPENS, DUE).outcome())
			.isEqualTo(AdmissionDecision.Outcome.ASSIGNMENT_CLOSED);
		assertThat(assignment(AssignmentStatus.ARCHIVED, false).canAccept(OPENS, DUE).outcome())
			.isEqualTo(AdmissionDecision.Outcome.ASSIGNMENT_ARCHIVED);
		assertThat(assignment(AssignmentStatus.SCHEDULED, false).canAccept(OPENS.minusNanos(1), DUE).outcome())
			.isEqualTo(AdmissionDecision.Outcome.NOT_YET_OPEN);
	}

	@Test
	@DisplayName("does not let a commit date beat the deadline")
	void commitDateCannotBeatTheDeadline() throws NoSuchMethodException {
		Assignment assignment = assignment(AssignmentStatus.OPEN, false);

		assertThat(assignment.canAccept(DUE.plusNanos(1), DUE).accepted()).isFalse();
		assertThat(Assignment.class.getMethod("canAccept", Instant.class, Instant.class).getParameterCount())
			.isEqualTo(2);
	}

	@Test
	@DisplayName("requires a complete, reproducible configuration and an ordered schedule to publish")
	void publishingRequiresCompleteReproducibleConfigurationAndOrderedSchedule() {
		Assignment draft = assignment(AssignmentStatus.DRAFT, false, null, null, null, OPENS, DUE);
		Assignment reversed = assignment(AssignmentStatus.DRAFT, false, UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), DUE, OPENS);

		assertThatThrownBy(() -> draft.changeStatus(AssignmentStatus.OPEN, CLOCK))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("template version");
		assertThatThrownBy(() -> reversed.changeStatus(AssignmentStatus.OPEN, CLOCK))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("opens_at before due_at");
	}

	private static Assignment assignment(AssignmentStatus status, boolean allowLate) {
		return assignment(status, allowLate, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), OPENS, DUE);
	}

	private static Assignment assignment(AssignmentStatus status, boolean allowLate, UUID templateId, UUID suiteId,
			UUID runtimeId, Instant opensAt, Instant dueAt) {
		AssignmentDefinition definition = new AssignmentDefinition(UUID.randomUUID(), "assignment-1", "Assignment",
				null, 0, status, true, opensAt, dueAt, "UTC", BigDecimal.valueOf(100), 10, BigDecimal.valueOf(100),
				allowLate, templateId, suiteId, runtimeId, 60, 1024L, BigDecimal.ONE, 16, false);
		return new Assignment(definition, CLOCK);
	}

}
