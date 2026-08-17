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

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import org.gitgrader.assignments.AdmissionDecision;
import org.gitgrader.assignments.AssignmentAdministration;
import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentDefinition;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.assignments.DeadlineExtensionView;
import org.gitgrader.assignments.domain.Assignment;
import org.gitgrader.assignments.domain.DeadlineExtension;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default assignment catalog and administration implementation. */
@Service
@Transactional
public class DefaultAssignmentService implements AssignmentCatalog, AssignmentAdministration {

	private final AssignmentRepository assignments;

	private final DeadlineExtensionRepository extensions;

	private final Clock clock;

	DefaultAssignmentService(AssignmentRepository assignments, DeadlineExtensionRepository extensions, Clock clock) {
		this.assignments = assignments;
		this.extensions = extensions;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<AssignmentView> findAssignment(UUID id) {
		return this.assignments.findById(id).map(Assignment::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<AssignmentView> findByCourse(UUID courseId) {
		return this.assignments.findByCourseIdOrderByDisplayOrderAsc(courseId)
			.stream()
			.map(Assignment::toView)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<AssignmentView> findAll() {
		return this.assignments.findAllByOrderByCourseIdAscDisplayOrderAscIdAsc()
			.stream()
			.map(Assignment::toView)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<DeadlineExtensionView> findExtensions(UUID assignmentId) {
		requireAssignment(assignmentId);
		return this.extensions.findByAssignmentIdOrderByGrantedAtDesc(assignmentId)
			.stream()
			.map(DeadlineExtension::toView)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Instant effectiveDueAt(UUID assignmentId, UUID studentId) {
		Assignment assignment = requireAssignment(assignmentId);
		return this.extensions.findByAssignmentIdAndStudentIdAndRevokedAtIsNull(assignmentId, studentId)
			.map(DeadlineExtension::extendedDueAt)
			.orElseGet(assignment::dueAt);
	}

	@Override
	@Transactional(readOnly = true)
	public AdmissionDecision canAccept(UUID assignmentId, UUID studentId, Instant serverReceivedAt) {
		Assignment assignment = requireAssignment(assignmentId);
		Instant extendedDueAt = this.extensions
			.findByAssignmentIdAndStudentIdAndRevokedAtIsNull(assignmentId, studentId)
			.map(DeadlineExtension::extendedDueAt)
			.orElse(null);
		return assignment.canAccept(serverReceivedAt, extendedDueAt);
	}

	@Override
	public AssignmentView create(AssignmentDefinition definition) {
		return this.assignments.save(new Assignment(definition, this.clock)).toView();
	}

	@Override
	public AssignmentView update(UUID assignmentId, AssignmentDefinition definition) {
		Assignment assignment = this.assignments.findById(assignmentId)
			.orElseThrow(() -> new EntityNotFoundException("Assignment not found: " + assignmentId));
		assignment.update(definition, this.clock);
		return this.assignments.save(assignment).toView();
	}

	@Override
	public AssignmentView changeStatus(UUID assignmentId, AssignmentStatus status) {
		Assignment assignment = requireAssignment(assignmentId);
		assignment.changeStatus(status, this.clock);
		return this.assignments.save(assignment).toView();
	}

	@Override
	public DeadlineExtensionView grantExtension(UUID assignmentId, UUID studentId, Instant extendedDueAt, String reason,
			String actor) {
		requireAssignment(assignmentId);
		if (this.extensions.existsByAssignmentIdAndStudentIdAndRevokedAtIsNull(assignmentId, studentId)) {
			throw new IllegalStateException("Student already has a live extension for this assignment");
		}
		DeadlineExtension extension = new DeadlineExtension(assignmentId, studentId, extendedDueAt, reason, actor,
				this.clock);
		return this.extensions.save(extension).toView();
	}

	@Override
	public DeadlineExtensionView revokeExtension(UUID assignmentId, UUID extensionId, String actor) {
		DeadlineExtension extension = this.extensions.findById(extensionId)
			.filter((candidate) -> candidate.toView().assignmentId().equals(assignmentId))
			.orElseThrow(() -> new EntityNotFoundException("Deadline extension not found: " + extensionId));
		extension.revoke(actor, this.clock);
		return this.extensions.save(extension).toView();
	}

	private Assignment requireAssignment(UUID id) {
		return this.assignments.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Assignment not found: " + id));
	}

}
