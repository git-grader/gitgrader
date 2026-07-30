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

package org.gitgrader.identity.domain;

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

import org.gitgrader.identity.Actor;
import org.gitgrader.identity.IllegalStateTransitionException;
import org.gitgrader.identity.StudentRegistration;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.identity.StudentView;
import org.jspecify.annotations.Nullable;

/** Student profile and its lifecycle rules. */
@Entity
@Table(name = "students")
public class Student {

	@Id
	private UUID id;

	@Column(name = "student_number", nullable = false)
	private String studentNumber;

	@Column(name = "first_name", nullable = false)
	private String firstName;

	@Column(name = "last_name", nullable = false)
	private String lastName;

	@Column(nullable = false)
	private String email;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private StudentStatus status;

	@Column(name = "class_label")
	private @Nullable String classLabel;

	@Column
	private @Nullable String notes;

	@Column(name = "registered_at", nullable = false)
	private Instant registeredAt;

	@Column(name = "verified_at")
	private @Nullable Instant verifiedAt;

	@Column(name = "verified_by")
	private @Nullable String verifiedBy;

	@Column(name = "suspended_at")
	private @Nullable Instant suspendedAt;

	@Column(name = "suspension_reason")
	private @Nullable String suspensionReason;

	@Column(name = "archived_at")
	private @Nullable Instant archivedAt;

	@Column(name = "anonymized_at")
	private @Nullable Instant anonymizedAt;

	@Column(name = "last_seen_at")
	private @Nullable Instant lastSeenAt;

	@Column(name = "registration_ip_hash")
	private @Nullable String registrationIpHash;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Student() {
	}

	/**
	 * Creates a self-registered profile.
	 * @param registration profile values
	 * @param clock source of registration time
	 */
	public Student(StudentRegistration registration, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.studentNumber = registration.studentNumber();
		this.firstName = registration.firstName();
		this.lastName = registration.lastName();
		this.email = registration.email();
		this.status = StudentStatus.SELF_REGISTERED;
		this.classLabel = registration.classLabel();
		this.registrationIpHash = registration.registrationIpHash();
		this.registeredAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Determines whether this student may submit under the deployment policy.
	 * @param requireInstructorVerification whether self-registration alone is
	 * insufficient
	 * @return true when submissions are permitted
	 */
	public boolean canSubmit(boolean requireInstructorVerification) {
		if (requireInstructorVerification) {
			return this.status == StudentStatus.VERIFIED_BY_INSTRUCTOR;
		}
		return this.status == StudentStatus.SELF_REGISTERED || this.status == StudentStatus.VERIFIED_BY_INSTRUCTOR;
	}

	/**
	 * Verifies a self-registered profile.
	 * @param actor instructor or administrator performing the action
	 * @param clock source of transition time
	 */
	public void verify(Actor actor, Clock clock) {
		requireStatus(StudentStatus.SELF_REGISTERED, "verify");
		Instant now = Instant.now(clock);
		this.status = StudentStatus.VERIFIED_BY_INSTRUCTOR;
		this.verifiedAt = now;
		this.verifiedBy = actor.id();
		this.updatedAt = now;
	}

	/**
	 * Suspends a profile that can currently submit.
	 * @param reason non-blank suspension reason
	 * @param actor instructor or administrator performing the action
	 * @param clock source of transition time
	 */
	public void suspend(String reason, Actor actor, Clock clock) {
		if (this.status != StudentStatus.SELF_REGISTERED && this.status != StudentStatus.VERIFIED_BY_INSTRUCTOR) {
			throw illegalTransition("suspend");
		}
		if (reason.isBlank()) {
			throw new IllegalArgumentException("Suspension reason must not be blank");
		}
		Instant now = Instant.now(clock);
		this.status = StudentStatus.SUSPENDED;
		this.suspendedAt = now;
		this.suspensionReason = reason;
		this.notes = "Suspended by " + actor.id();
		this.updatedAt = now;
	}

	/**
	 * Archives a non-archived profile.
	 * @param clock source of transition time
	 */
	public void archive(Clock clock) {
		if (this.status == StudentStatus.ARCHIVED) {
			throw illegalTransition("archive");
		}
		Instant now = Instant.now(clock);
		this.status = StudentStatus.ARCHIVED;
		this.archivedAt = now;
		this.updatedAt = now;
	}

	/**
	 * Replaces personal fields with stable non-reversible placeholders.
	 * @param clock source of transition time
	 */
	public void anonymize(Clock clock) {
		if (this.anonymizedAt != null) {
			throw illegalTransition("anonymize");
		}
		Instant now = Instant.now(clock);
		String placeholder = this.id.toString();
		this.firstName = "Anonymous";
		this.lastName = placeholder;
		this.email = "anonymous-" + placeholder + "@invalid";
		this.studentNumber = "anonymous-" + placeholder;
		this.anonymizedAt = now;
		this.updatedAt = now;
	}

	/**
	 * Returns the stable profile identifier retained by submission references.
	 * @return student identifier
	 */
	public UUID id() {
		return this.id;
	}

	/**
	 * Returns when personal fields were anonymized.
	 * @return anonymization time, or null before anonymization
	 */
	public @Nullable Instant anonymizedAt() {
		return this.anonymizedAt;
	}

	/**
	 * Converts this entity to the public read model.
	 * @return student view
	 */
	public StudentView toView() {
		return new StudentView(this.id, this.studentNumber, this.firstName + " " + this.lastName, this.email,
				this.status, this.classLabel, this.registeredAt);
	}

	private void requireStatus(StudentStatus required, String operation) {
		if (this.status != required) {
			throw illegalTransition(operation);
		}
	}

	private IllegalStateTransitionException illegalTransition(String operation) {
		return new IllegalStateTransitionException("Cannot " + operation + " student in state " + this.status);
	}

}
