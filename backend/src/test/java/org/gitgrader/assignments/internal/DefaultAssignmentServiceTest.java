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

package org.gitgrader.assignments.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import org.gitgrader.assignments.AdmissionDecision;
import org.gitgrader.assignments.AssignmentDefinition;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.DeadlineExtensionView;
import org.gitgrader.assignments.domain.Assignment;
import org.gitgrader.assignments.domain.DeadlineExtension;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultAssignmentServiceTest {

	private static final UUID COURSE_ID = UUID.randomUUID();

	private static final Instant NOW = Instant.parse("2026-03-01T10:00:00Z");

	private static final Instant DUE = Instant.parse("2026-03-01T12:00:00Z");

	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	void effectiveDueAtUsesLiveExtensionAndOtherwiseAssignmentDueAt() {
		AssignmentRepository assignments = mock(AssignmentRepository.class);
		DeadlineExtensionRepository extensions = mock(DeadlineExtensionRepository.class);
		DefaultAssignmentService service = new DefaultAssignmentService(assignments, extensions, CLOCK);
		UUID assignmentId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		Assignment assignment = assignment();
		DeadlineExtension extension = new DeadlineExtension(assignmentId, studentId, DUE.plusSeconds(3600), "reason",
				"actor", CLOCK);
		when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment));
		when(extensions.findByAssignmentIdAndStudentIdAndRevokedAtIsNull(assignmentId, studentId))
			.thenReturn(Optional.of(extension))
			.thenReturn(Optional.empty());

		assertThat(service.effectiveDueAt(assignmentId, studentId)).isEqualTo(DUE.plusSeconds(3600));
		assertThat(service.effectiveDueAt(assignmentId, studentId)).isEqualTo(DUE);
	}

	@Test
	void everyExtensionOnAnAssignmentIsListedIncludingRevokedOnes() {
		AssignmentRepository assignments = mock(AssignmentRepository.class);
		DeadlineExtensionRepository extensions = mock(DeadlineExtensionRepository.class);
		DefaultAssignmentService service = new DefaultAssignmentService(assignments, extensions, CLOCK);
		UUID assignmentId = UUID.randomUUID();
		DeadlineExtension live = new DeadlineExtension(assignmentId, UUID.randomUUID(), DUE.plusSeconds(3600), "live",
				"actor", CLOCK);
		DeadlineExtension revoked = new DeadlineExtension(assignmentId, UUID.randomUUID(), DUE.plusSeconds(7200),
				"revoked", "actor", CLOCK);
		revoked.revoke("actor", CLOCK);
		when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment()));
		when(extensions.findByAssignmentIdOrderByGrantedAtDesc(assignmentId)).thenReturn(List.of(live, revoked));

		assertThat(service.findExtensions(assignmentId)).extracting(DeadlineExtensionView::reason)
			.containsExactly("live", "revoked");
	}

	@Test
	void listingExtensionsOfAnAssignmentThatDoesNotExistIsRefused() {
		AssignmentRepository assignments = mock(AssignmentRepository.class);
		DeadlineExtensionRepository extensions = mock(DeadlineExtensionRepository.class);
		DefaultAssignmentService service = new DefaultAssignmentService(assignments, extensions, CLOCK);
		UUID assignmentId = UUID.randomUUID();
		when(assignments.findById(assignmentId)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.findExtensions(assignmentId)).isInstanceOf(EntityNotFoundException.class);
	}

	@Test
	void exactlyOneLiveExtensionIsEnforcedBeforePersistence() {
		AssignmentRepository assignments = mock(AssignmentRepository.class);
		DeadlineExtensionRepository extensions = mock(DeadlineExtensionRepository.class);
		DefaultAssignmentService service = new DefaultAssignmentService(assignments, extensions, CLOCK);
		UUID assignmentId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment()));
		when(extensions.existsByAssignmentIdAndStudentIdAndRevokedAtIsNull(assignmentId, studentId)).thenReturn(true);

		assertThatThrownBy(
				() -> service.grantExtension(assignmentId, studentId, DUE.plusSeconds(3600), "reason", "actor"))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("live extension");
	}

	@Test
	void revokingFreesTheLiveExtensionSlotForAnotherGrant() {
		AssignmentRepository assignments = mock(AssignmentRepository.class);
		DeadlineExtensionRepository extensions = mock(DeadlineExtensionRepository.class);
		DefaultAssignmentService service = new DefaultAssignmentService(assignments, extensions, CLOCK);
		UUID assignmentId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		DeadlineExtension old = new DeadlineExtension(assignmentId, studentId, DUE.plusSeconds(3600), "old", "actor",
				CLOCK);
		UUID oldId = old.toView().id();
		when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment()));
		when(extensions.findById(oldId)).thenReturn(Optional.of(old));
		when(extensions.save(old)).thenReturn(old);
		when(extensions.existsByAssignmentIdAndStudentIdAndRevokedAtIsNull(assignmentId, studentId)).thenReturn(false);
		DeadlineExtension replacement = new DeadlineExtension(assignmentId, studentId, DUE.plusSeconds(7200), "new",
				"actor", CLOCK);
		when(extensions.save(org.mockito.ArgumentMatchers.any(DeadlineExtension.class))).thenReturn(replacement);

		service.revokeExtension(oldId, "actor");
		assertThat(service.grantExtension(assignmentId, studentId, DUE.plusSeconds(7200), "new", "actor").reason())
			.isEqualTo("new");
	}

	@Test
	void refusesAPushToADraftThatHasNoDueDateRatherThanFailing() {
		// Reachable without doing anything unusual: publish an assignment, which
		// provisions a repository for every enrolled student, then take it back to draft
		// and clear the date - a draft is not required to have one. The student still
		// holds the repository, and their next push asked for a due date the assignment
		// no longer has. The state alone answers this, and asking for the date first
		// turned "not published yet" into a failed push nobody could explain.
		AssignmentRepository assignments = mock(AssignmentRepository.class);
		DeadlineExtensionRepository extensions = mock(DeadlineExtensionRepository.class);
		DefaultAssignmentService service = new DefaultAssignmentService(assignments, extensions, CLOCK);
		UUID assignmentId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();
		Assignment assignment = assignment();
		assignment.changeStatus(AssignmentStatus.DRAFT, CLOCK);
		assignment.update(draftWithoutDates(), CLOCK);
		when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment));
		when(extensions.findByAssignmentIdAndStudentIdAndRevokedAtIsNull(assignmentId, studentId))
			.thenReturn(Optional.empty());

		AdmissionDecision decision = service.canAccept(assignmentId, studentId, NOW);

		assertThat(decision.outcome()).isEqualTo(AdmissionDecision.Outcome.ASSIGNMENT_DRAFT);
		assertThat(decision.accepted()).isFalse();
	}

	private static AssignmentDefinition draftWithoutDates() {
		return new AssignmentDefinition(COURSE_ID, "assignment-1", "Assignment", null, 0, AssignmentStatus.DRAFT, true,
				null, null, "UTC", BigDecimal.valueOf(100), 10, BigDecimal.valueOf(100), false, null, null, null, 60,
				1024L, BigDecimal.ONE, 16, false);
	}

	private static Assignment assignment() {
		return new Assignment(new AssignmentDefinition(COURSE_ID, "assignment-1", "Assignment", null, 0,
				AssignmentStatus.OPEN, true, NOW, DUE, "UTC", BigDecimal.valueOf(100), 10, BigDecimal.valueOf(100),
				false, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), 60, 1024L, BigDecimal.ONE, 16, false),
				CLOCK);
	}

}
