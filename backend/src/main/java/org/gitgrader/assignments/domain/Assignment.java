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
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.gitgrader.assignments.AdmissionDecision;
import org.gitgrader.assignments.AssignmentDefinition;
import org.gitgrader.assignments.AssignmentIdentityMismatchException;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.jspecify.annotations.Nullable;

/** Assignment aggregate enforcing publication completeness and receive-time admission. */
@Entity
@Table(name = "assignments")
public class Assignment {

	@Id
	private UUID id;

	@Column(name = "course_id", nullable = false)
	private UUID courseId;

	@Column(name = "assignment_key", nullable = false)
	private String assignmentKey;

	@Column(nullable = false)
	private String title;

	@Column
	private @Nullable String description;

	@Column(name = "display_order", nullable = false)
	private int displayOrder;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AssignmentStatus status;

	@Column(nullable = false)
	private boolean mandatory;

	@Column(name = "opens_at")
	private @Nullable Instant opensAt;

	@Column(name = "due_at")
	private @Nullable Instant dueAt;

	@Column
	private @Nullable String timezone;

	@Column(name = "max_points", nullable = false)
	private BigDecimal maxPoints;

	@Column(name = "test_count", nullable = false)
	private int testCount;

	@Column(name = "pass_threshold", nullable = false)
	private BigDecimal passThreshold;

	@Column(name = "allow_late", nullable = false)
	private boolean allowLate;

	@Column(name = "template_version_id")
	private @Nullable UUID templateVersionId;

	@Column(name = "test_suite_version_id")
	private @Nullable UUID testSuiteVersionId;

	@Column(name = "runtime_id")
	private @Nullable UUID runtimeId;

	@Column(name = "timeout_seconds")
	private @Nullable Integer timeoutSeconds;

	@Column(name = "memory_limit_bytes")
	private @Nullable Long memoryLimitBytes;

	@Column(name = "cpu_limit")
	private @Nullable BigDecimal cpuLimit;

	@Column(name = "pid_limit")
	private @Nullable Integer pidLimit;

	@Column(name = "network_enabled", nullable = false)
	private boolean networkEnabled;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Assignment() {
	}

	/**
	 * Creates an assignment and validates any non-draft initial status.
	 * @param definition assignment values
	 * @param clock application clock
	 */
	public Assignment(AssignmentDefinition definition, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.createdAt = now;
		this.updatedAt = now;
		apply(definition);
		validatePublication(this.status);
	}

	/**
	 * Replaces the values of a draft assignment while preserving its identity.
	 *
	 * <p>
	 * A draft is allowed to remain incomplete. An instructor assembles an assignment over
	 * several visits, attaching a template before the test suite it will be graded
	 * against exists, so completeness is checked when publishing rather than here.
	 * @param definition replacement assignment values
	 * @param clock application clock
	 */
	public void update(AssignmentDefinition definition, Clock clock) {
		if (this.status != AssignmentStatus.DRAFT) {
			throw new IllegalStateException("Only draft assignments can be updated");
		}
		if (definition.status() != AssignmentStatus.DRAFT) {
			throw new IllegalStateException("An assignment update must retain draft status");
		}
		if (!this.courseId.equals(definition.courseId())) {
			throw new AssignmentIdentityMismatchException("courseId cannot be changed");
		}
		if (!this.assignmentKey.equals(definition.assignmentKey())) {
			throw new AssignmentIdentityMismatchException("assignmentKey cannot be changed");
		}
		apply(definition);
		this.updatedAt = Instant.now(clock);
	}

	/**
	 * Moves the assignment to a lifecycle state.
	 * @param status target state
	 * @param clock application clock
	 */
	public void changeStatus(AssignmentStatus status, Clock clock) {
		validatePublication(status);
		this.status = status;
		this.updatedAt = Instant.now(clock);
	}

	/**
	 * Evaluates a server receive timestamp against assignment state and due time.
	 * @param serverReceivedAt server-controlled receive timestamp
	 * @param effectiveDueAt student-specific due timestamp
	 * @return distinct admission decision
	 */
	public AdmissionDecision canAccept(Instant serverReceivedAt, Instant effectiveDueAt) {
		AdmissionDecision.Outcome stateOutcome = stateOutcome(serverReceivedAt);
		if (stateOutcome != AdmissionDecision.Outcome.ACCEPTED) {
			return new AdmissionDecision(stateOutcome, false, effectiveDueAt);
		}
		boolean late = serverReceivedAt.isAfter(effectiveDueAt);
		AdmissionDecision.Outcome deadlineOutcome = late && !this.allowLate ? AdmissionDecision.Outcome.PAST_DEADLINE
				: AdmissionDecision.Outcome.ACCEPTED;
		return new AdmissionDecision(deadlineOutcome, late, effectiveDueAt);
	}

	/**
	 * Returns the default due instant required for published assignments.
	 * @return assignment due instant
	 */
	public Instant dueAt() {
		if (this.dueAt == null) {
			throw new IllegalStateException("Draft assignment has no due date");
		}
		return this.dueAt;
	}

	/**
	 * Converts this entity to its public read model.
	 * @return assignment view
	 */
	public AssignmentView toView() {
		return new AssignmentView(this.id, this.courseId, this.assignmentKey, this.title, this.description,
				this.displayOrder, this.status, this.mandatory, this.opensAt, this.dueAt, this.timezone, this.maxPoints,
				this.testCount, this.passThreshold, this.allowLate, this.templateVersionId, this.testSuiteVersionId,
				this.runtimeId, this.timeoutSeconds, this.memoryLimitBytes, this.cpuLimit, this.pidLimit,
				this.networkEnabled);
	}

	private void apply(AssignmentDefinition definition) {
		this.courseId = definition.courseId();
		this.assignmentKey = definition.assignmentKey();
		this.title = definition.title();
		this.description = definition.description();
		this.displayOrder = definition.displayOrder();
		this.status = definition.status();
		this.mandatory = definition.mandatory();
		this.opensAt = definition.opensAt();
		this.dueAt = definition.dueAt();
		this.timezone = definition.timezone();
		this.maxPoints = definition.maxPoints();
		this.testCount = definition.testCount();
		this.passThreshold = definition.passThreshold();
		this.allowLate = definition.allowLate();
		this.templateVersionId = definition.templateVersionId();
		this.testSuiteVersionId = definition.testSuiteVersionId();
		this.runtimeId = definition.runtimeId();
		this.timeoutSeconds = definition.timeoutSeconds();
		this.memoryLimitBytes = definition.memoryLimitBytes();
		this.cpuLimit = definition.cpuLimit();
		this.pidLimit = definition.pidLimit();
		this.networkEnabled = definition.networkEnabled();
	}

	private AdmissionDecision.Outcome stateOutcome(Instant receivedAt) {
		return switch (this.status) {
			case DRAFT -> AdmissionDecision.Outcome.ASSIGNMENT_DRAFT;
			case CLOSED -> AdmissionDecision.Outcome.ASSIGNMENT_CLOSED;
			case ARCHIVED -> AdmissionDecision.Outcome.ASSIGNMENT_ARCHIVED;
			case SCHEDULED, OPEN -> this.opensAt != null && receivedAt.isBefore(this.opensAt)
					? AdmissionDecision.Outcome.NOT_YET_OPEN : AdmissionDecision.Outcome.ACCEPTED;
		};
	}

	private void validatePublication(AssignmentStatus targetStatus) {
		if (targetStatus == AssignmentStatus.DRAFT) {
			return;
		}
		if (this.templateVersionId == null || this.testSuiteVersionId == null || this.runtimeId == null
				|| this.opensAt == null || this.dueAt == null || !this.opensAt.isBefore(this.dueAt)) {
			throw new IllegalStateException(
					"Publishing requires template version, test-suite version, runtime, and opens_at before due_at");
		}
	}

}
