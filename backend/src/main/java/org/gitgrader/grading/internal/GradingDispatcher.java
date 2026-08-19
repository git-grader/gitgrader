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
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.internal.GradingQueue.ClaimedWork;
import org.gitgrader.grading.internal.GradingQueue.ClaimedJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
 * Work is claimed by {@code SELECT ... FOR UPDATE SKIP LOCKED} and run on a pool bounded
 * by {@code grading.max-parallel-jobs}, and no more is claimed than that pool has free
 * threads. Every claim carries a lease generation, so a worker that lost its lease - to
 * expiry, or to a shutdown that handed the job back - cannot write the result it was
 * still computing. Starting a second instance of the application is therefore a valid way
 * to add grading capacity.
 */
@Component
// A runner-role instance serves sandboxes and must not also claim jobs: two workers on
// one queue is not wrong, but a runner that grades is a runner that needs the database,
// the repositories and the workspaces, which is the opposite of why it was split out.
@ConditionalOnProperty(name = "grading.queue.enabled", havingValue = "true", matchIfMissing = true)
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

	private final ExecutorService workers;

	private final String workerId = "worker-" + UUID.randomUUID();

	private final AtomicBoolean accepting = new AtomicBoolean();

	private final AtomicInteger inFlight = new AtomicInteger();

	private final Map<UUID, Future<?>> active = new ConcurrentHashMap<>();

	public GradingDispatcher(GradingQueue queue, GradingExecutor executor, GradingProperties properties,
			ExecutorService workers) {
		this.queue = queue;
		this.executor = executor;
		this.properties = properties;
		this.workers = workers;
	}

	/**
	 * Claims and runs whatever work is due.
	 */
	@Scheduled(fixedDelayString = "${grading.queue.poll-interval}")
	public void poll() {
		if (!this.accepting.get()) {
			return;
		}
		try {
			this.queue.reapAbandonedClaims();
			int capacity = this.properties.maxParallelJobs() - this.inFlight.get();
			if (capacity <= 0) {
				return;
			}
			for (ClaimedJob lease : this.queue.claimBatch(this.workerId, capacity)) {
				if (!this.accepting.get()) {
					// Shutdown began after the batch was claimed. Everything still held
					// is
					// handed back by stop(), so returning here loses nothing.
					return;
				}
				submit(lease);
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
		cancelActive();
		try {
			this.queue.requeueHeldJobs(this.workerId);
		}
		catch (RuntimeException ex) {
			// Losing the database here only means the lease has to expire instead, which
			// is slower but still correct. It must never break the shutdown sequence.
			logger.warn("Could not return in-flight grading jobs to the queue", ex);
		}
	}

	private void submit(ClaimedJob lease) {
		this.inFlight.incrementAndGet();
		try {
			FutureTask<Void> task = new FutureTask<>(() -> {
				process(lease);
				return null;
			});
			this.active.put(lease.jobId(), task);
			this.workers.execute(task);
		}
		catch (RuntimeException ex) {
			this.inFlight.decrementAndGet();
			throw ex;
		}
	}

	private void cancelActive() {
		this.active.values().forEach((future) -> future.cancel(true));
		long deadline = System.nanoTime() + this.properties.queue().drainTimeout().toNanos();
		while (this.inFlight.get() > 0 && System.nanoTime() < deadline) {
			try {
				TimeUnit.MILLISECONDS.sleep(DRAIN_POLL.toMillis());
			}
			catch (InterruptedException ex) {
				Thread.currentThread().interrupt();
				return;
			}
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
	 * @param lease the claim this worker holds on the job
	 */
	void process(ClaimedJob lease) {
		Path workspace = null;
		UUID runId = null;
		try {
			Optional<ClaimedWork> work = this.queue.load(lease);
			if (work.isEmpty()) {
				logger.warn("Claimed grading job {} disappeared before it could run", lease.jobId());
				return;
			}

			runId = work.get().run().id();
			// The lease was lost between claiming and starting, so another worker owns
			// this job now and anything this one produced must not be written.
			if (!this.queue.markRunning(lease, runId)) {
				return;
			}

			GradingExecutor.Outcome outcome = this.executor.execute(work.get().run(), lease.claimExpiresAt());
			workspace = outcome.workspace();
			if (Thread.currentThread().isInterrupted()) {
				return;
			}
			this.queue.recordSuccess(lease, runId, outcome);
		}
		catch (RuntimeException ex) {
			if (runId != null) {
				this.queue.recordFailure(lease, runId, ex);
			}
		}
		finally {
			this.inFlight.decrementAndGet();
			this.active.remove(lease.jobId());
			if (workspace != null) {
				this.executor.discardWorkspace(workspace);
			}
		}
	}

}
