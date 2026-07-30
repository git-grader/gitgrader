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

	public GradingJob(UUID gradingRunId, UUID submissionId, int maxAttempts, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.gradingRunId = gradingRunId;
		this.submissionId = submissionId;
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

	public GradingJobStatus status() {
		return this.status;
	}

	public int attempts() {
		return this.attempts;
	}

	public @Nullable String lastError() {
		return this.lastError;
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
