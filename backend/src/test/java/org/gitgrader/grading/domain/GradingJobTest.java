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

package org.gitgrader.grading.domain;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.gitgrader.grading.GradingJobStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Tests for the {@link GradingJob} queue lifecycle.
 *
 * <p>
 * These rules are what let the platform run without a message broker. If a lease never
 * expired a crashed worker would strand a student's submission forever, and if a failure
 * retried without backoff one broken job could occupy every worker.
 */
class GradingJobTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-01T12:00:00Z"), ZoneOffset.UTC);

	private static final Duration LEASE = Duration.ofMinutes(15);

	private static final Duration BACKOFF = Duration.ofSeconds(30);

	@Test
	@DisplayName("a new job is pending and unclaimed")
	void startsPending() {
		GradingJob job = newJob(3);

		assertThat(job.status()).isEqualTo(GradingJobStatus.PENDING);
		assertThat(job.attempts()).isZero();
		assertThat(job.lastError()).isNull();
	}

	@Test
	@DisplayName("claiming takes a lease and counts the attempt")
	void claimingCountsTheAttempt() {
		GradingJob job = newJob(3);

		job.claim("worker-1", LEASE, CLOCK);

		// The attempt is counted at claim time, not at completion: a worker that dies
		// mid-run must still burn an attempt, otherwise a job that always kills its
		// worker would retry forever.
		assertThat(job.status()).isEqualTo(GradingJobStatus.CLAIMED);
		assertThat(job.attempts()).isEqualTo(1);
		assertThat(job.status().isClaimed()).isTrue();
	}

	@Test
	@DisplayName("a failure below the attempt ceiling returns the job to the queue")
	void retriesUntilTheCeiling() {
		GradingJob job = newJob(3);
		job.claim("worker-1", LEASE, CLOCK);

		boolean willRetry = job.recordFailure("image pull failed", BACKOFF, CLOCK);

		assertThat(willRetry).isTrue();
		assertThat(job.status()).isEqualTo(GradingJobStatus.PENDING);
		assertThat(job.lastError()).isEqualTo("image pull failed");
	}

	@Test
	@DisplayName("gives up once the attempts are exhausted")
	void givesUpAtTheCeiling() {
		GradingJob job = newJob(2);

		job.claim("worker-1", LEASE, CLOCK);
		assertThat(job.recordFailure("first", BACKOFF, CLOCK)).isTrue();
		job.claim("worker-2", LEASE, CLOCK);
		boolean willRetry = job.recordFailure("second", BACKOFF, CLOCK);

		assertThat(willRetry).isFalse();
		assertThat(job.status()).isEqualTo(GradingJobStatus.FAILED);
		assertThat(job.attempts()).isEqualTo(2);
	}

	@Test
	@DisplayName("a failure clears the claim so another worker can pick the job up")
	void failureClearsTheClaim() {
		GradingJob job = newJob(3);
		job.claim("worker-1", LEASE, CLOCK);

		job.recordFailure("boom", BACKOFF, CLOCK);

		// Leaving the claim in place would make the job invisible to the dispatcher and
		// only recoverable by the reaper, delaying the retry by a whole lease period.
		assertThat(job.status()).isEqualTo(GradingJobStatus.PENDING);
		assertThat(job.status().isClaimed()).isFalse();
	}

	@Test
	@DisplayName("moves through running to done")
	void completesSuccessfully() {
		GradingJob job = newJob(3);
		job.claim("worker-1", LEASE, CLOCK);

		job.markRunning(CLOCK);
		assertThat(job.status()).isEqualTo(GradingJobStatus.RUNNING);
		assertThat(job.status().isClaimed()).isTrue();

		job.markDone(CLOCK);
		assertThat(job.status()).isEqualTo(GradingJobStatus.DONE);
		assertThat(job.status().isClaimed()).isFalse();
	}

	@Test
	@DisplayName("only claimed and running jobs are reclaimable by the reaper")
	void identifiesReclaimableStates() {
		assertThat(GradingJobStatus.CLAIMED.isClaimed()).isTrue();
		assertThat(GradingJobStatus.RUNNING.isClaimed()).isTrue();
		assertThat(GradingJobStatus.PENDING.isClaimed()).isFalse();
		assertThat(GradingJobStatus.DONE.isClaimed()).isFalse();
		assertThat(GradingJobStatus.FAILED.isClaimed()).isFalse();
		assertThat(GradingJobStatus.CANCELLED.isClaimed()).isFalse();
	}

	@Test
	@DisplayName("keeps the run and submission it belongs to")
	void keepsItsIdentifiers() {
		UUID run = UUID.randomUUID();
		UUID submission = UUID.randomUUID();
		UUID student = UUID.randomUUID();
		UUID course = UUID.randomUUID();
		UUID assignment = UUID.randomUUID();

		GradingJob job = new GradingJob(run, submission, student, course, assignment, 3, CLOCK);

		assertThat(job.gradingRunId()).isEqualTo(run);
		assertThat(job.submissionId()).isEqualTo(submission);
		assertThat(job.studentId()).isEqualTo(student);
		assertThat(job.courseId()).isEqualTo(course);
		assertThat(job.assignmentId()).isEqualTo(assignment);
		assertThat(job.id()).isNotNull();
	}

	@Test
	@DisplayName("supersedes a queued job")
	void cancelsAPendingJob() {
		GradingJob job = newJob(3);

		job.cancel(CLOCK);

		assertThat(job.status()).isEqualTo(GradingJobStatus.CANCELLED);
	}

	@Test
	@DisplayName("refuses to supersede work a worker already claimed")
	void refusesToCancelClaimedWork() {
		GradingJob job = newJob(3);
		job.claim("worker", Duration.ofMinutes(15), CLOCK);

		assertThatIllegalStateException().isThrownBy(() -> job.cancel(CLOCK))
			.withMessageContaining("Only a pending job can be superseded");
	}

	@Test
	@DisplayName("refunds the attempt a claim consumed when the worker shuts down")
	void requeueAfterShutdownRefundsTheAttempt() {
		GradingJob job = newJob(3);
		job.claim("worker", Duration.ofMinutes(15), CLOCK);
		assertThat(job.attempts()).isOne();

		job.requeueAfterShutdown(CLOCK);

		assertThat(job.status()).isEqualTo(GradingJobStatus.PENDING);
		assertThat(job.attempts()).isZero();
	}

	@Test
	@DisplayName("never drives the attempt count below zero")
	void requeueAfterShutdownWithoutAClaimIsHarmless() {
		GradingJob job = newJob(3);

		job.requeueAfterShutdown(CLOCK);

		assertThat(job.attempts()).isZero();
		assertThat(job.status()).isEqualTo(GradingJobStatus.PENDING);
	}

	private static GradingJob newJob(int maxAttempts) {
		return new GradingJob(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), maxAttempts, CLOCK);
	}

}
