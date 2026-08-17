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
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.gitgrader.submissions.NewSubmission;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Tests for {@link Submission} and the {@link NewSubmission} builder.
 *
 * <p>
 * A submission is the record a disputed grade is argued from, so the properties asserted
 * here are the ones that make it defensible: the receive time is the server's, the
 * version pins are captured, and a refused push cannot be quietly relabelled later.
 */
class SubmissionTest {

	private static final Instant RECEIVED = Instant.parse("2026-04-01T09:15:00Z");

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-01T09:15:01Z"), ZoneOffset.UTC);

	@Test
	@DisplayName("records the server receive time, not the commit's own timestamp")
	void recordsServerReceiveTime() {
		// A commit date is client controlled: a student can set it to any value. Only the
		// server-side receive time may decide lateness, so it is stored separately.
		Instant clientClaimedTime = Instant.parse("2020-01-01T00:00:00Z");
		Submission submission = new Submission(
				base().commit("a".repeat(40), "refs/heads/main", "Solve", clientClaimedTime).build(), CLOCK);

		assertThat(submission.receivedAt()).isEqualTo(RECEIVED);
		assertThat(submission.commitAuthoredAt()).isEqualTo(clientClaimedTime);
		assertThat(submission.receivedAt()).isNotEqualTo(submission.commitAuthoredAt());
	}

	@Test
	@DisplayName("captures the versions in force so the run can be reproduced later")
	void capturesVersionPins() {
		UUID template = UUID.randomUUID();
		UUID suite = UUID.randomUUID();
		UUID runtime = UUID.randomUUID();
		String digest = "sha256:" + "b".repeat(64);

		Submission submission = new Submission(base().pins(template, suite, runtime, digest).build(), CLOCK);

		assertThat(submission.templateVersionId()).isEqualTo(template);
		assertThat(submission.testSuiteVersionId()).isEqualTo(suite);
		assertThat(submission.runtimeId()).isEqualTo(runtime);
		assertThat(submission.runtimeImageDigest()).isEqualTo(digest);
	}

	@Test
	@DisplayName("advances the cached grading status")
	void advancesStatus() {
		Submission submission = new Submission(base().build(), CLOCK);
		assertThat(submission.status()).isEqualTo(SubmissionStatus.RECEIVED);

		submission.updateStatus(SubmissionStatus.QUEUED);
		submission.updateStatus(SubmissionStatus.RUNNING);
		submission.updateStatus(SubmissionStatus.PASSED);

		assertThat(submission.status()).isEqualTo(SubmissionStatus.PASSED);
	}

	@Test
	@DisplayName("freezes a rejected submission so the refusal reason survives")
	void rejectedSubmissionIsFinal() {
		// Letting a later grading run overwrite this would erase why the push was
		// refused,
		// which is the only record the student and instructor have of the incident.
		Submission submission = new Submission(base().rejected("Commit was not signed").build(), CLOCK);

		assertThat(submission.status()).isEqualTo(SubmissionStatus.REJECTED);
		assertThat(submission.rejectionReason()).isEqualTo("Commit was not signed");
		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> submission.updateStatus(SubmissionStatus.PASSED))
			.withMessageContaining("final");
	}

	@Test
	@DisplayName("a terminal grading status cannot regress")
	void terminalStatusCannotRegress() {
		Submission submission = new Submission(base().build(), CLOCK);
		submission.updateStatus(SubmissionStatus.QUEUED);
		submission.updateStatus(SubmissionStatus.RUNNING);
		submission.updateStatus(SubmissionStatus.PASSED);

		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> submission.updateStatus(SubmissionStatus.RUNNING))
			.withMessageContaining("final");
	}

	@Test
	@DisplayName("a graded submission can be queued again, because that is what a regrade is")
	void regradeRequeuesAGradedSubmission() {
		// The orchestrator queues a regrade through the same path a push takes, so
		// refusing every transition out of a finished status made the second attempt
		// impossible rather than making the first one safe.
		for (SubmissionStatus finished : List.of(SubmissionStatus.PASSED, SubmissionStatus.FAILED,
				SubmissionStatus.INFRASTRUCTURE_ERROR, SubmissionStatus.CANCELLED)) {
			Submission submission = new Submission(base().build(), CLOCK);
			submission.updateStatus(SubmissionStatus.QUEUED);
			submission.updateStatus(SubmissionStatus.RUNNING);
			submission.updateStatus(finished);

			submission.updateStatus(SubmissionStatus.QUEUED);

			assertThat(submission.status()).isEqualTo(SubmissionStatus.QUEUED);
		}
	}

	@Test
	@DisplayName("stores a bounded commit subject, however long the one pushed was")
	void boundsTheCommitSubject() {
		// commit_message is unbounded TEXT and a push may carry objects up to
		// git.max-file-size, so a megabyte-long subject was stored whole and then sent to
		// every browser that listed the submission.
		String enormous = "x".repeat(10_000);

		Submission submission = new Submission(base().commit("a".repeat(40), "refs/heads/main", enormous, null).build(),
				CLOCK);

		assertThat(submission.commitMessage()).hasSize(4096);
	}

	@Test
	@DisplayName("abbreviates the commit hash the way git does")
	void abbreviatesCommitHash() {
		Submission submission = new Submission(
				base().commit("8f31c20abcdef" + "0".repeat(27), "refs/heads/main", "Solve", RECEIVED).build(), CLOCK);

		assertThat(submission.shortCommitSha()).isEqualTo("8f31c20").hasSize(7);
	}

	@Test
	@DisplayName("refuses to build a submission that is missing a required identifier")
	void builderRefusesIncompleteRecord() {
		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> NewSubmission.builder().receivedAt(RECEIVED).build())
			.withMessageContaining("repositoryId");
	}

	@Test
	@DisplayName("refuses to build a submission with no receive time")
	void builderRefusesMissingReceiveTime() {
		assertThatExceptionOfType(IllegalStateException.class)
			.isThrownBy(() -> NewSubmission.builder()
				.target(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
				.build())
			.withMessageContaining("receivedAt");
	}

	@Test
	@DisplayName("records the signing key and the transport key separately")
	void recordsBothKeys() {
		// They are usually the same key but need not be: a student may push over one key
		// and sign with another, and attribution has to survive either being revoked.
		UUID signingKey = UUID.randomUUID();
		UUID transportKey = UUID.randomUUID();

		Submission submission = new Submission(
				base().signature(SignatureVerdict.VERIFIED, signingKey, "SHA256:sign", transportKey).build(), CLOCK);

		assertThat(submission.signatureKeyId()).isEqualTo(signingKey);
		assertThat(submission.transportKeyId()).isEqualTo(transportKey);
		assertThat(submission.signatureStatus()).isEqualTo(SignatureVerdict.VERIFIED);
	}

	@Test
	@DisplayName("flags a late submission alongside the deadline that applied")
	void flagsLateness() {
		Instant due = Instant.parse("2026-03-31T23:59:59Z");
		Submission submission = new Submission(base().admission(SubmissionStatus.RECEIVED, true, due).build(), CLOCK);

		assertThat(submission.late()).isTrue();
		assertThat(submission.effectiveDueAt()).isEqualTo(due);
	}

	private static NewSubmission.Builder base() {
		return NewSubmission.builder()
			.target(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID())
			.commit("c".repeat(40), "refs/heads/main", "Solve the assignment", RECEIVED)
			.receivedAt(RECEIVED)
			.signature(SignatureVerdict.VERIFIED, UUID.randomUUID(), "SHA256:key", UUID.randomUUID());
	}

}
