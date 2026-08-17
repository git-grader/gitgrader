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

package org.gitgrader.submissions;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Everything needed to record one push, gathered before anything is written.
 *
 * <p>
 * A parameter object rather than a long constructor signature, for one specific reason:
 * these fields are the immutable historical facts of a submission, and a positional
 * argument list of twenty-odd {@code UUID}s is a defect waiting to happen. The builder
 * makes each value name itself at the call site.
 *
 * @param repositoryId the student's repository for this assignment
 * @param repositoryPath where that repository lives, captured so grading need not resolve
 * it
 * @param studentId who pushed
 * @param courseId owning course
 * @param assignmentId the assignment being answered
 * @param commitSha the pushed commit
 * @param gitRef the ref that was updated
 * @param commitMessage the commit subject and body
 * @param commitAuthoredAt the client-supplied authoring time, recorded for display only
 * @param receivedAt the server-side receive time, which is what deadlines are judged on
 * @param signatureStatus the recorded signature outcome
 * @param signatureKeyId the registered key that produced the signature
 * @param signatureFingerprint fingerprint of the signing key
 * @param transportKeyId the registered key that authenticated the SSH connection
 * @param templateVersionId template version in force at the time
 * @param testSuiteVersionId hidden test suite version in force at the time
 * @param runtimeId runtime in force at the time
 * @param runtimeImageDigest immutable image digest actually used
 * @param status initial status
 * @param late whether the push arrived after the effective deadline
 * @param effectiveDueAt the deadline that applied, including any extension
 * @param rejectionReason why the push was refused, when it was
 */
public record NewSubmission(UUID repositoryId, @Nullable String repositoryPath, UUID studentId, UUID courseId,
		UUID assignmentId, String commitSha, String gitRef, @Nullable String commitMessage,
		@Nullable Instant commitAuthoredAt, Instant receivedAt, SignatureVerdict signatureStatus,
		@Nullable UUID signatureKeyId, @Nullable String signatureFingerprint, @Nullable UUID transportKeyId,
		@Nullable UUID templateVersionId, @Nullable UUID testSuiteVersionId, @Nullable UUID runtimeId,
		@Nullable String runtimeImageDigest, SubmissionStatus status, boolean late, @Nullable Instant effectiveDueAt,
		@Nullable String rejectionReason) {

	/** Long enough for any subject a person writes, short enough to store and render. */
	private static final int MAX_COMMIT_MESSAGE_LENGTH = 4096;

	/**
	 * Starts building a submission record.
	 * @return a new builder
	 */
	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Fluent builder for {@link NewSubmission}.
	 */
	public static final class Builder {

		private @Nullable UUID repositoryId;

		private @Nullable String repositoryPath;

		private @Nullable UUID studentId;

		private @Nullable UUID courseId;

		private @Nullable UUID assignmentId;

		private String commitSha = "";

		private String gitRef = "";

		private @Nullable String commitMessage;

		private @Nullable Instant commitAuthoredAt;

		private @Nullable Instant receivedAt;

		private SignatureVerdict signatureStatus = SignatureVerdict.UNSIGNED;

		private @Nullable UUID signatureKeyId;

		private @Nullable String signatureFingerprint;

		private @Nullable UUID transportKeyId;

		private @Nullable UUID templateVersionId;

		private @Nullable UUID testSuiteVersionId;

		private @Nullable UUID runtimeId;

		private @Nullable String runtimeImageDigest;

		private SubmissionStatus status = SubmissionStatus.RECEIVED;

		private boolean late;

		private @Nullable Instant effectiveDueAt;

		private @Nullable String rejectionReason;

		private Builder() {
		}

		/**
		 * Sets who and what this submission belongs to.
		 * @param repository the student's repository
		 * @param student who pushed
		 * @param course owning course
		 * @param assignment the assignment
		 * @return this builder
		 */
		public Builder target(UUID repository, UUID student, UUID course, UUID assignment) {
			this.repositoryId = repository;
			this.studentId = student;
			this.courseId = course;
			this.assignmentId = assignment;
			return this;
		}

		/**
		 * Records where the repository lives on disk.
		 * @param path repository path relative to the repository root
		 * @return this builder
		 */
		public Builder repositoryPath(@Nullable String path) {
			this.repositoryPath = path;
			return this;
		}

		/**
		 * Sets the commit that was pushed.
		 * @param sha the commit hash
		 * @param ref the updated ref
		 * @param message the commit message
		 * @param authoredAt the client-supplied authoring time
		 * @return this builder
		 */
		public Builder commit(String sha, String ref, @Nullable String message, @Nullable Instant authoredAt) {
			this.commitSha = sha;
			this.gitRef = ref;
			this.commitMessage = truncate(message);
			this.commitAuthoredAt = authoredAt;
			return this;
		}

		/**
		 * Bounds the stored subject.
		 *
		 * <p>
		 * The column is unbounded TEXT and a push may carry objects up to
		 * {@code git.max-file-size}, so a commit whose subject was megabytes long was
		 * stored whole and then serialised into every submissions page that listed it.
		 * Nothing reads more of a subject than this.
		 * @param message the subject as the commit carries it
		 * @return the subject, cut to a length a person would read
		 */
		private static @Nullable String truncate(@Nullable String message) {
			if (message == null || message.length() <= MAX_COMMIT_MESSAGE_LENGTH) {
				return message;
			}
			return message.substring(0, MAX_COMMIT_MESSAGE_LENGTH);
		}

		/**
		 * Sets the server-side receive time.
		 *
		 * <p>
		 * Deliberately separate from the commit's authoring time. The authoring time
		 * comes from the client and a student controls it completely, so only this value
		 * is ever used to judge a deadline.
		 * @param instant when the server accepted the push
		 * @return this builder
		 */
		public Builder receivedAt(Instant instant) {
			this.receivedAt = instant;
			return this;
		}

		/**
		 * Sets the signature outcome and the keys involved.
		 * @param verdict the recorded outcome
		 * @param signingKeyId the registered key that signed
		 * @param fingerprint fingerprint of the signing key
		 * @param transportKey the registered key that authenticated the connection
		 * @return this builder
		 */
		public Builder signature(SignatureVerdict verdict, @Nullable UUID signingKeyId, @Nullable String fingerprint,
				@Nullable UUID transportKey) {
			this.signatureStatus = verdict;
			this.signatureKeyId = signingKeyId;
			this.signatureFingerprint = fingerprint;
			this.transportKeyId = transportKey;
			return this;
		}

		/**
		 * Pins the versions that were in force, so the run can be reproduced later.
		 * @param templateVersion template version
		 * @param testSuiteVersion hidden test suite version
		 * @param runtime runtime definition
		 * @param imageDigest immutable image digest
		 * @return this builder
		 */
		public Builder pins(@Nullable UUID templateVersion, @Nullable UUID testSuiteVersion, @Nullable UUID runtime,
				@Nullable String imageDigest) {
			this.templateVersionId = templateVersion;
			this.testSuiteVersionId = testSuiteVersion;
			this.runtimeId = runtime;
			this.runtimeImageDigest = imageDigest;
			return this;
		}

		/**
		 * Records the admission outcome.
		 * @param initialStatus starting status
		 * @param wasLate whether the push arrived after the effective deadline
		 * @param dueAt the deadline that applied, including any extension
		 * @return this builder
		 */
		public Builder admission(SubmissionStatus initialStatus, boolean wasLate, @Nullable Instant dueAt) {
			this.status = initialStatus;
			this.late = wasLate;
			this.effectiveDueAt = dueAt;
			return this;
		}

		/**
		 * Records why a push was refused.
		 * @param reason the technical reason
		 * @return this builder
		 */
		public Builder rejected(String reason) {
			this.status = SubmissionStatus.REJECTED;
			this.rejectionReason = reason;
			return this;
		}

		/**
		 * Builds the immutable record.
		 * @return the gathered submission details
		 * @throws IllegalStateException if a required value was never supplied
		 */
		public NewSubmission build() {
			return new NewSubmission(required(this.repositoryId, "repositoryId"), this.repositoryPath,
					required(this.studentId, "studentId"), required(this.courseId, "courseId"),
					required(this.assignmentId, "assignmentId"), this.commitSha, this.gitRef, this.commitMessage,
					this.commitAuthoredAt, required(this.receivedAt, "receivedAt"), this.signatureStatus,
					this.signatureKeyId, this.signatureFingerprint, this.transportKeyId, this.templateVersionId,
					this.testSuiteVersionId, this.runtimeId, this.runtimeImageDigest, this.status, this.late,
					this.effectiveDueAt, this.rejectionReason);
		}

		private static <T> T required(@Nullable T value, String name) {
			if (value == null) {
				throw new IllegalStateException("A submission cannot be recorded without " + name);
			}
			return value;
		}

	}

}
