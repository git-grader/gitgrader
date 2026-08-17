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

package org.gitgrader.grading.internal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.FailureCategory;
import org.gitgrader.grading.GradingJobStatus;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.domain.GradingJob;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * The transactional half of the grading worker.
 *
 * <p>
 * <strong>Why this is a separate bean.</strong> Spring's {@code @Transactional} works
 * through a proxy, so a method calling another method on {@code this} bypasses it
 * entirely. An earlier version had the dispatcher's scheduled {@code poll()} calling its
 * own transactional methods directly, and the modifying queue query failed at runtime
 * with {@code TransactionRequiredException} - a failure that compiles perfectly and only
 * appears once the scheduler fires. Splitting the transactional operations into their own
 * bean makes the proxy boundary real rather than a matter of remembering.
 *
 * <p>
 * Each method is a short transaction on purpose. The slow part - the sandbox - runs
 * between them, outside any transaction, so a grading run never pins a database
 * connection for its duration.
 */
@Component
public class GradingQueue {

	/** How much of a failure message is kept on the job row. */
	private static final int MAX_ERROR_LENGTH = 2_000;

	private static final Logger logger = LoggerFactory.getLogger(GradingQueue.class);

	private final GradingJobRepository jobs;

	private final GradingRunRepository runs;

	private final GradingExecutor executor;

	private final SubmissionService submissions;

	private final GradingProperties properties;

	private final Clock clock;

	public GradingQueue(GradingJobRepository jobs, GradingRunRepository runs, GradingExecutor executor,
			SubmissionService submissions, GradingProperties properties, Clock clock) {
		this.jobs = jobs;
		this.runs = runs;
		this.executor = executor;
		this.submissions = submissions;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Returns jobs whose worker lease expired to the queue.
	 * @return how many jobs were released
	 */
	@Transactional
	public int reapAbandonedClaims() {
		int released = this.jobs.releaseExpiredClaims(Instant.now(this.clock));
		if (released > 0) {
			logger.warn("Returned {} abandoned grading job(s) to the queue", released);
		}
		return released;
	}

	/**
	 * Claims up to the configured parallelism in one short transaction.
	 * @param worker identifies the claiming worker
	 * @return ids of the jobs this worker now owns
	 */
	@Transactional
	public List<ClaimedJob> claimBatch(String worker, int capacity) {
		List<UUID> claimed = this.jobs.claimNext(Instant.now(this.clock), capacity);
		List<ClaimedJob> leases = claimed.stream().map(this.jobs::findById).flatMap(Optional::stream).map((job) -> {
			job.claim(worker, this.properties.queue().claimTimeout(), this.clock);
			return new ClaimedJob(job.id(), worker, job.leaseGeneration(), job.claimExpiresAt());
		}).toList();
		this.jobs.flush();
		return leases;
	}

	/**
	 * Hands every job this worker still holds back to the queue.
	 *
	 * <p>
	 * Called when the process is shutting down. Without it, an in-flight job stays
	 * {@code CLAIMED} until its lease runs out - fifteen minutes by default - during
	 * which the student sees a submission stuck in {@code RUNNING} and no worker will
	 * touch it.
	 *
	 * <p>
	 * The attempt each job consumed is refunded, because a redeploy is not the student's
	 * doing. That is the difference between this and {@link #reapAbandonedClaims}: an
	 * expired lease might belong to a job that hangs its worker and must stay bounded,
	 * but an orderly shutdown says nothing at all about the job.
	 * @param worker the worker that is stopping
	 * @return how many jobs were returned
	 */
	@Transactional
	public int requeueHeldJobs(String worker) {
		List<GradingJob> held = this.jobs.findByClaimedByAndStatusIn(worker,
				List.of(GradingJobStatus.CLAIMED, GradingJobStatus.RUNNING));
		for (GradingJob job : held) {
			job.requeueAfterShutdown(this.clock);
			this.runs.findById(job.gradingRunId()).ifPresent((run) -> {
				run.requeue();
				this.submissions.markStatus(run.submissionId(), SubmissionStatus.QUEUED);
			});
		}
		if (!held.isEmpty()) {
			logger.info("Returned {} in-flight grading job(s) to the queue for {}", held.size(), worker);
		}
		return held.size();
	}

	/**
	 * Loads a claimed job together with its run.
	 * @param jobId the claimed job
	 * @return the pair, or empty when either has disappeared
	 */
	@Transactional(readOnly = true)
	public Optional<ClaimedWork> load(ClaimedJob lease) {
		return this.jobs.findById(lease.jobId())
			.filter((job) -> job.status() == GradingJobStatus.CLAIMED && job.leaseGeneration() == lease.generation())
			.flatMap((job) -> this.runs.findById(job.gradingRunId()).map((run) -> new ClaimedWork(job, run)));
	}

	/**
	 * Marks a job and its run as executing.
	 * @param jobId the job
	 * @param runId the run
	 */
	@Transactional
	public boolean markRunning(ClaimedJob lease, UUID runId) {
		Instant now = Instant.now(this.clock);
		if (this.jobs.markRunningIfOwned(lease.jobId(), lease.worker(), lease.generation(), now) == 0) {
			logger.warn("Ignoring stale grading claim {} generation {} for worker {}", lease.jobId(),
					lease.generation(), lease.worker());
			return false;
		}
		this.runs.findById(runId).ifPresent((run) -> {
			run.markRunning(this.clock);
			this.submissions.markStatus(run.submissionId(), SubmissionStatus.RUNNING);
		});
		return true;
	}

	/**
	 * Writes the outcome of a finished run.
	 * @param jobId the job
	 * @param runId the run
	 * @param outcome what the sandbox produced
	 */
	@Transactional
	public boolean recordSuccess(ClaimedJob lease, UUID runId, GradingExecutor.Outcome outcome) {
		if (this.jobs.lockRunningLease(lease.jobId(), lease.worker(), lease.generation(), Instant.now(this.clock))
			.isEmpty()) {
			logger.warn("Ignoring stale grading success for job {} generation {} from worker {}", lease.jobId(),
					lease.generation(), lease.worker());
			return false;
		}
		GradingRun run = this.runs.findById(runId).orElseThrow();
		this.executor.persist(run, outcome);
		this.jobs.findById(lease.jobId()).ifPresent((job) -> job.markDone(this.clock));

		SubmissionStatus status = resolveSubmissionStatus(run);
		this.submissions.markStatus(run.submissionId(), status);
		logger.info("Grading run {} finished: {} ({} of {} tests passed) [correlationId={}]", run.id(), status,
				run.testsPassed(), run.testsTotal(), run.correlationId());
		return true;
	}

	/**
	 * Records a failed attempt and decides whether it will be retried.
	 * @param jobId the job
	 * @param runId the run
	 * @param cause what went wrong
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public boolean recordFailure(ClaimedJob lease, UUID runId, Throwable cause) {
		if (this.jobs.lockRunningLease(lease.jobId(), lease.worker(), lease.generation(), Instant.now(this.clock))
			.isEmpty()) {
			logger.warn("Ignoring stale grading failure for job {} generation {} from worker {}", lease.jobId(),
					lease.generation(), lease.worker());
			return false;
		}
		String message = String.valueOf(cause.getMessage());
		String truncated = (message.length() > MAX_ERROR_LENGTH) ? message.substring(0, MAX_ERROR_LENGTH) : message;

		GradingJob job = this.jobs.findById(lease.jobId()).orElseThrow();
		boolean willRetry = job.recordFailure(truncated, this.properties.queue().retryBackoff(), this.clock);

		if (!willRetry) {
			// Only once the retries are exhausted does this become the submission's
			// outcome, and even then it is an infrastructure error, never a failed test.
			this.runs.findById(runId).ifPresent((run) -> {
				run.fail(FailureCategory.INFRASTRUCTURE_ERROR, truncated, GradingRunStatus.INFRASTRUCTURE_ERROR,
						this.clock);
				this.submissions.markStatus(run.submissionId(), SubmissionStatus.INFRASTRUCTURE_ERROR);
			});
		}
		logger.error("Grading job {} failed (attempt {}, retry={})", lease.jobId(), job.attempts(), willRetry, cause);
		return true;
	}

	/**
	 * Maps a finished run onto the submission's cached status.
	 *
	 * <p>
	 * An infrastructure failure never becomes {@code FAILED}: that status means the
	 * student's tests did not pass, and using it for a platform problem would show them a
	 * grade they did not earn.
	 * @param run the finished run
	 * @return the status to cache on the submission
	 */
	private SubmissionStatus resolveSubmissionStatus(GradingRun run) {
		FailureCategory category = run.failureCategory();
		if (category != null && !category.isStudentFault()) {
			return SubmissionStatus.INFRASTRUCTURE_ERROR;
		}
		return Boolean.TRUE.equals(run.passed()) ? SubmissionStatus.PASSED : SubmissionStatus.FAILED;
	}

	/**
	 * A claimed job together with the run it belongs to.
	 *
	 * @param job the claimed job
	 * @param run the run it will execute
	 */
	public record ClaimedWork(GradingJob job, GradingRun run) {
	}

	/** A persisted lease identity carried by one worker execution. */
	public record ClaimedJob(UUID jobId, String worker, long generation, Instant claimExpiresAt) {
	}

}
