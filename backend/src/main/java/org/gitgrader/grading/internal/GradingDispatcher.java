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

import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.internal.GradingQueue.ClaimedWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Polls the queue and runs grading work.
 *
 * <p>
 * Holds no transaction of its own. Every database interaction goes through
 * {@link GradingQueue}, which is a separate bean precisely so the calls cross Spring's
 * proxy boundary - see that class for what happens when they do not.
 *
 * <p>
 * The worker is deliberately simple: no thread pool, no in-memory queue. All concurrency
 * control lives in the {@code SELECT ... FOR UPDATE SKIP LOCKED} claim, so starting a
 * second instance of the application is a valid way to add grading capacity.
 */
@Component
public class GradingDispatcher implements SmartLifecycle {

	/**
	 * Stops after the transports, so no push or request can queue work into a queue that
	 * is already draining.
	 */
	static final int SHUTDOWN_PHASE = Integer.MAX_VALUE - 1024;

	private static final Duration DRAIN_POLL = Duration.ofMillis(200);

	private static final Logger logger = LoggerFactory.getLogger(GradingDispatcher.class);

	private final GradingQueue queue;

	private final GradingExecutor executor;

	private final GradingProperties properties;

	private final String workerId = "worker-" + UUID.randomUUID();

	private final AtomicBoolean accepting = new AtomicBoolean();

	private final AtomicInteger inFlight = new AtomicInteger();

	public GradingDispatcher(GradingQueue queue, GradingExecutor executor, GradingProperties properties) {
		this.queue = queue;
		this.executor = executor;
		this.properties = properties;
	}

	/**
	 * Claims and runs whatever work is due.
	 */
	@Scheduled(fixedDelayString = "${grading.queue.poll-interval:2s}")
	public void poll() {
		if (!this.accepting.get()) {
			return;
		}
		try {
			this.queue.reapAbandonedClaims();
			for (UUID jobId : this.queue.claimBatch(this.workerId)) {
				if (!this.accepting.get()) {
					// Shutdown began after the batch was claimed. Everything still held
					// is
					// handed back by stop(), so returning here loses nothing.
					return;
				}
				process(jobId);
			}
		}
		catch (RuntimeException ex) {
			// The scheduler stops invoking a task that throws, which would silently halt
			// all grading. Every failure is swallowed here and retried on the next tick.
			logger.error("Grading dispatcher tick failed", ex);
		}
	}

	@Override
	public void start() {
		this.accepting.set(true);
	}

	@Override
	public boolean isRunning() {
		return this.accepting.get();
	}

	String workerId() {
		return this.workerId;
	}

	@Override
	public int getPhase() {
		return SHUTDOWN_PHASE;
	}

	/**
	 * Stops claiming, lets a run in progress finish if it can, and hands back the rest.
	 *
	 * <p>
	 * A grading run can legitimately take minutes, and blocking a redeploy for that long
	 * is worse than repeating the work: a run is reproducible, because it re-materialises
	 * from an immutable commit. So the wait is bounded by
	 * {@code grading.queue.drain-timeout} - long enough for a short run to land - and
	 * anything still executing is returned to the queue with its attempt refunded.
	 *
	 * <p>
	 * The abandoned container is left to Docker, which removes it on exit because every
	 * sandbox is created with auto-remove.
	 */
	@Override
	public void stop() {
		this.accepting.set(false);
		awaitQuiet(this.properties.queue().drainTimeout());
		try {
			this.queue.requeueHeldJobs(this.workerId);
		}
		catch (RuntimeException ex) {
			// Losing the database here only means the lease has to expire instead, which
			// is slower but still correct. It must never break the shutdown sequence.
			logger.warn("Could not return in-flight grading jobs to the queue", ex);
		}
	}

	private void awaitQuiet(Duration timeout) {
		long deadline = System.nanoTime() + timeout.toNanos();
		while (this.inFlight.get() > 0 && System.nanoTime() < deadline) {
			try {
				Thread.sleep(DRAIN_POLL.toMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
		}
		if (this.inFlight.get() > 0) {
			logger.info("Shutdown drain timed out with {} run(s) still executing; returning them to the queue",
					this.inFlight.get());
		}
	}

	/**
	 * Runs one claimed job to completion.
	 *
	 * <p>
	 * The sandbox call sits between short transactions rather than inside one, so a run
	 * that takes minutes never holds a database connection open.
	 * @param jobId the claimed job
	 */
	void process(UUID jobId) {
		Optional<ClaimedWork> work = this.queue.load(jobId);
		if (work.isEmpty()) {
			logger.warn("Claimed grading job {} disappeared before it could run", jobId);
			return;
		}

		UUID runId = work.get().run().id();
		this.queue.markRunning(jobId, runId);

		this.inFlight.incrementAndGet();
		Path workspace = null;
		try {
			GradingExecutor.Outcome outcome = this.executor.execute(work.get().run());
			workspace = outcome.workspace();
			this.queue.recordSuccess(jobId, runId, outcome);
		}
		catch (RuntimeException ex) {
			this.queue.recordFailure(jobId, runId, ex);
		}
		finally {
			this.inFlight.decrementAndGet();
			if (workspace != null) {
				this.executor.discardWorkspace(workspace);
			}
		}
	}

}
