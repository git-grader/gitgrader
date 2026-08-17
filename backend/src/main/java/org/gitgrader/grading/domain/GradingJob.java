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
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.gitgrader.grading.GradingJobStatus;
import org.jspecify.annotations.Nullable;

/**
 * One unit of grading work waiting in the database-backed queue.
 *
 * <p>
 * The queue is a table rather than a broker. For the load this platform sees, PostgreSQL
 * with {@code SELECT ... FOR UPDATE SKIP LOCKED} gives exactly the semantics needed, and
 * it removes an entire service from the self-hosting story - which is the difference
 * between an operator running two containers and running five.
 *
 * <p>
 * A claim is a lease, not a lock. {@link #claimExpiresAt} is what makes a crashed worker
 * recoverable: the reaper returns any job whose lease ran out, so work is never stranded
 * by a process that died mid-run.
 */
@Entity
@Table(name = "grading_jobs")
public class GradingJob {

	/** Default queue priority; lower numbers are dispatched first. */
	private static final int DEFAULT_PRIORITY = 100;

	@Id
	private UUID id;

	@Column(name = "grading_run_id", nullable = false, updatable = false)
	private UUID gradingRunId;

	@Column(name = "submission_id", nullable = false, updatable = false)
	private UUID submissionId;

	/**
	 * Whose work this is, copied from the submission rather than reached through it.
	 *
	 * <p>
	 * The claim query schedules one job per student inside
	 * {@code FOR UPDATE SKIP LOCKED}. Joining to {@code submissions} to establish that
	 * would widen the lock footprint and lose the partial indexes that keep the dispatch
	 * scan proportional to runnable work.
	 */
	@Column(name = "student_id", nullable = false, updatable = false)
	private UUID studentId;

	@Column(name = "course_id", nullable = false, updatable = false)
	private UUID courseId;

	@Column(name = "assignment_id", nullable = false, updatable = false)
	private UUID assignmentId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GradingJobStatus status;

	@Column(nullable = false)
	private int priority;

	@Column(nullable = false)
	private int attempts;

	@Column(name = "max_attempts", nullable = false)
	private int maxAttempts;

	@Column(name = "available_at", nullable = false)
	private Instant availableAt;

	@Column(name = "claimed_at")
	private @Nullable Instant claimedAt;

	@Column(name = "claimed_by")
	private @Nullable String claimedBy;

	@Column(name = "claim_expires_at")
	private @Nullable Instant claimExpiresAt;

	@Column(name = "lease_generation", nullable = false)
	private long leaseGeneration;

	@Column(name = "finished_at")
	private @Nullable Instant finishedAt;

	@Column(name = "last_error")
	private @Nullable String lastError;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected GradingJob() {
		// Required by JPA.
	}

	public GradingJob(UUID gradingRunId, UUID submissionId, UUID studentId, UUID courseId, UUID assignmentId,
			int maxAttempts, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.gradingRunId = gradingRunId;
		this.submissionId = submissionId;
		this.studentId = studentId;
		this.courseId = courseId;
		this.assignmentId = assignmentId;
		this.status = GradingJobStatus.PENDING;
		this.priority = DEFAULT_PRIORITY;
		this.maxAttempts = maxAttempts;
		this.availableAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID id() {
		return this.id;
	}

	public UUID gradingRunId() {
		return this.gradingRunId;
	}

	public UUID submissionId() {
		return this.submissionId;
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

	public GradingJobStatus status() {
		return this.status;
	}

	public int attempts() {
		return this.attempts;
	}

	public @Nullable String lastError() {
		return this.lastError;
	}

	public @Nullable Instant claimExpiresAt() {
		return this.claimExpiresAt;
	}

	public long leaseGeneration() {
		return this.leaseGeneration;
	}

	/**
	 * Takes a lease on this job.
	 * @param worker identifies the claiming worker, for diagnostics
	 * @param leaseDuration how long the claim is valid before the reaper may reclaim it
	 * @param clock the application clock
	 */
	public void claim(String worker, Duration leaseDuration, Clock clock) {
		Instant now = Instant.now(clock);
		this.status = GradingJobStatus.CLAIMED;
		this.claimedBy = worker;
		this.claimedAt = now;
		this.claimExpiresAt = now.plus(leaseDuration);
		this.leaseGeneration++;
		this.attempts++;
		this.updatedAt = now;
	}

	/**
	 * Marks the job as executing.
	 * @param clock the application clock
	 */
	public void markRunning(Clock clock) {
		this.status = GradingJobStatus.RUNNING;
		this.updatedAt = Instant.now(clock);
	}

	/**
	 * Marks the job finished successfully.
	 * @param clock the application clock
	 */
	public void markDone(Clock clock) {
		this.status = GradingJobStatus.DONE;
		this.finishedAt = Instant.now(clock);
		this.updatedAt = this.finishedAt;
	}

	/**
	 * Withdraws unstarted work because a newer submission superseded it.
	 * @param clock the application clock
	 * @throws IllegalStateException if the job already left the queue
	 */
	public void cancel(Clock clock) {
		if (this.status != GradingJobStatus.PENDING) {
			// Cancelling a claimed job would abandon a sandbox that is already running
			// and
			// leave its workspace behind. Superseding only ever discards work not
			// started.
			throw new IllegalStateException("Only a pending job can be superseded, but this one is " + this.status);
		}
		this.status = GradingJobStatus.CANCELLED;
		this.finishedAt = Instant.now(clock);
		this.updatedAt = this.finishedAt;
	}

	/**
	 * Returns this job to the queue because the worker is shutting down.
	 *
	 * <p>
	 * Refunds the attempt {@link #claim} consumed. A shutdown is the platform's doing,
	 * and counting it against {@link #maxAttempts} would let three redeploys during a
	 * long run exhaust a submission and report an infrastructure error the student cannot
	 * act on. The reaper path deliberately does not refund: a lease that expired without
	 * an orderly shutdown may be a job that hangs its worker, and that must stay bounded.
	 * @param clock the application clock
	 */
	public void requeueAfterShutdown(Clock clock) {
		Instant now = Instant.now(clock);
		this.status = GradingJobStatus.PENDING;
		this.claimedBy = null;
		this.claimedAt = null;
		this.claimExpiresAt = null;
		this.availableAt = now;
		if (this.attempts > 0) {
			this.attempts--;
		}
		this.updatedAt = now;
	}

	/**
	 * Records a failed attempt and decides whether to retry.
	 *
	 * <p>
	 * A retry is scheduled by moving {@link #availableAt} into the future rather than by
	 * sleeping, so a failing job cannot occupy a worker while it backs off.
	 * @param error what went wrong, truncated by the caller
	 * @param backoff how long to wait before the job becomes available again
	 * @param clock the application clock
	 * @return true when the job will be retried, false when it is exhausted
	 */
	public boolean recordFailure(String error, Duration backoff, Clock clock) {
		Instant now = Instant.now(clock);
		this.lastError = error;
		this.updatedAt = now;
		this.claimedBy = null;
		this.claimedAt = null;
		this.claimExpiresAt = null;
		if (this.attempts >= this.maxAttempts) {
			this.status = GradingJobStatus.FAILED;
			this.finishedAt = now;
			return false;
		}
		this.status = GradingJobStatus.PENDING;
		this.availableAt = now.plus(backoff);
		return true;
	}

}
