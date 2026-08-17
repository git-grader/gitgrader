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

package org.gitgrader.submissions.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.gitgrader.submissions.NewSubmission;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionStatus;
import org.jspecify.annotations.Nullable;

/**
 * One accepted push, recorded permanently.
 *
 * <p>
 * <strong>What is immutable here and why.</strong> Everything that describes <em>what was
 * submitted</em> - the commit, the receive time, the signature outcome, and the template,
 * test suite and runtime versions in force at the time - is written once by the
 * constructor and has no setter. That is what allows a grade to be reconstructed and
 * defended long after the assignment closed, and why re-grading appends a new grading run
 * instead of touching this row.
 *
 * <p>
 * {@link #status} is the one exception. It is not history but a cached projection of the
 * latest grading run, kept on the row so that listing a course does not need a join per
 * submission. The grading pipeline is the single writer for it.
 *
 * <p>
 * A version column protects the mutable status projection from stale grading workers.
 */
@Entity
@Table(name = "submissions")
public class Submission {

	private static final Map<SubmissionStatus, Set<SubmissionStatus>> STATUS_TRANSITIONS = Map.of(
			SubmissionStatus.RECEIVED, EnumSet.of(SubmissionStatus.QUEUED, SubmissionStatus.CANCELLED),
			SubmissionStatus.QUEUED, EnumSet.of(SubmissionStatus.RUNNING, SubmissionStatus.CANCELLED),
			SubmissionStatus.RUNNING, EnumSet.of(SubmissionStatus.QUEUED, SubmissionStatus.PASSED,
					SubmissionStatus.FAILED, SubmissionStatus.INFRASTRUCTURE_ERROR));

	/** Number of hash characters shown in abbreviated output, matching git's default. */
	private static final int SHORT_SHA_LENGTH = 7;

	@Id
	private UUID id;

	@Column(name = "repository_id", nullable = false, updatable = false)
	private UUID repositoryId;

	@Column(name = "repository_path", updatable = false)
	private @Nullable String repositoryPath;

	@Column(name = "student_id", nullable = false, updatable = false)
	private UUID studentId;

	@Column(name = "course_id", nullable = false, updatable = false)
	private UUID courseId;

	@Column(name = "assignment_id", nullable = false, updatable = false)
	private UUID assignmentId;

	@Column(name = "commit_sha", nullable = false, updatable = false)
	private String commitSha;

	@Column(name = "git_ref", nullable = false, updatable = false)
	private String gitRef;

	@Column(name = "commit_message", updatable = false)
	private @Nullable String commitMessage;

	@Column(name = "commit_authored_at", updatable = false)
	private @Nullable Instant commitAuthoredAt;

	@Column(name = "received_at", nullable = false, updatable = false)
	private Instant receivedAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "signature_status", nullable = false, updatable = false)
	private SignatureVerdict signatureStatus;

	@Column(name = "signature_key_id", updatable = false)
	private @Nullable UUID signatureKeyId;

	@Column(name = "signature_fingerprint", updatable = false)
	private @Nullable String signatureFingerprint;

	@Column(name = "transport_key_id", updatable = false)
	private @Nullable UUID transportKeyId;

	@Column(name = "template_version_id", updatable = false)
	private @Nullable UUID templateVersionId;

	@Column(name = "test_suite_version_id", updatable = false)
	private @Nullable UUID testSuiteVersionId;

	@Column(name = "runtime_id", updatable = false)
	private @Nullable UUID runtimeId;

	@Column(name = "runtime_image_digest", updatable = false)
	private @Nullable String runtimeImageDigest;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private SubmissionStatus status;

	@Column(nullable = false, updatable = false)
	private boolean late;

	@Column(name = "effective_due_at", updatable = false)
	private @Nullable Instant effectiveDueAt;

	@Column(name = "rejection_reason", updatable = false)
	private @Nullable String rejectionReason;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Version
	private long version;

	protected Submission() {
		// Required by JPA.
	}

	public Submission(NewSubmission details, Clock clock) {
		this.id = UUID.randomUUID();
		this.repositoryId = details.repositoryId();
		this.repositoryPath = details.repositoryPath();
		this.studentId = details.studentId();
		this.courseId = details.courseId();
		this.assignmentId = details.assignmentId();
		this.commitSha = details.commitSha();
		this.gitRef = details.gitRef();
		this.commitMessage = details.commitMessage();
		this.commitAuthoredAt = details.commitAuthoredAt();
		this.receivedAt = details.receivedAt();
		this.signatureStatus = details.signatureStatus();
		this.signatureKeyId = details.signatureKeyId();
		this.signatureFingerprint = details.signatureFingerprint();
		this.transportKeyId = details.transportKeyId();
		this.templateVersionId = details.templateVersionId();
		this.testSuiteVersionId = details.testSuiteVersionId();
		this.runtimeId = details.runtimeId();
		this.runtimeImageDigest = details.runtimeImageDigest();
		this.status = details.status();
		this.late = details.late();
		this.effectiveDueAt = details.effectiveDueAt();
		this.rejectionReason = details.rejectionReason();
		this.createdAt = Instant.now(clock);
	}

	public UUID id() {
		return this.id;
	}

	public UUID repositoryId() {
		return this.repositoryId;
	}

	public @Nullable String repositoryPath() {
		return this.repositoryPath;
	}

	public UUID studentId() {
		return this.studentId;
	}

	public UUID courseId() {
		return this.courseId;
	}

	public UUID assignmentId() {
		return this.assignmentId;
	}

	public String commitSha() {
		return this.commitSha;
	}

	public String gitRef() {
		return this.gitRef;
	}

	public @Nullable String commitMessage() {
		return this.commitMessage;
	}

	public @Nullable Instant commitAuthoredAt() {
		return this.commitAuthoredAt;
	}

	public Instant receivedAt() {
		return this.receivedAt;
	}

	public SignatureVerdict signatureStatus() {
		return this.signatureStatus;
	}

	public @Nullable UUID signatureKeyId() {
		return this.signatureKeyId;
	}

	public @Nullable String signatureFingerprint() {
		return this.signatureFingerprint;
	}

	public @Nullable UUID transportKeyId() {
		return this.transportKeyId;
	}

	public @Nullable UUID templateVersionId() {
		return this.templateVersionId;
	}

	public @Nullable UUID testSuiteVersionId() {
		return this.testSuiteVersionId;
	}

	public @Nullable UUID runtimeId() {
		return this.runtimeId;
	}

	public @Nullable String runtimeImageDigest() {
		return this.runtimeImageDigest;
	}

	public SubmissionStatus status() {
		return this.status;
	}

	public boolean late() {
		return this.late;
	}

	public @Nullable Instant effectiveDueAt() {
		return this.effectiveDueAt;
	}

	public @Nullable String rejectionReason() {
		return this.rejectionReason;
	}

	public Instant createdAt() {
		return this.createdAt;
	}

	/**
	 * An abbreviated commit hash, for display only.
	 * @return the first seven characters of the commit hash
	 */
	public String shortCommitSha() {
		return this.commitSha.length() <= SHORT_SHA_LENGTH ? this.commitSha
				: this.commitSha.substring(0, SHORT_SHA_LENGTH);
	}

	/**
	 * Advances the cached grading status.
	 *
	 * <p>
	 * The only mutation this entity permits. A rejected submission is frozen: its status
	 * records why the push was refused, and letting a later grading run overwrite that
	 * would erase the reason.
	 * @param next the new status
	 * @throws IllegalStateException if the submission was rejected
	 */
	public void updateStatus(SubmissionStatus next) {
		if (this.status == next) {
			return;
		}
		boolean allowed = STATUS_TRANSITIONS.getOrDefault(this.status, EnumSet.noneOf(SubmissionStatus.class))
			.contains(next);
		if (!allowed) {
			throw new IllegalStateException(
					"Submission status " + this.status + " is final or cannot advance to " + next);
		}
		this.status = next;
	}

}
