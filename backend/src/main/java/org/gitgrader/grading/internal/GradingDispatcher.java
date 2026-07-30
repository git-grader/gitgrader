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
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.grading.internal.GradingQueue.ClaimedWork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class GradingDispatcher {

	private static final Logger logger = LoggerFactory.getLogger(GradingDispatcher.class);

	private final GradingQueue queue;

	private final GradingExecutor executor;

	private final String workerId = "worker-" + UUID.randomUUID();

	public GradingDispatcher(GradingQueue queue, GradingExecutor executor) {
		this.queue = queue;
		this.executor = executor;
	}

	/**
	 * Claims and runs whatever work is due.
	 */
	@Scheduled(fixedDelayString = "${grading.queue.poll-interval:2s}")
	public void poll() {
		try {
			this.queue.reapAbandonedClaims();
			for (UUID jobId : this.queue.claimBatch(this.workerId)) {
				process(jobId);
			}
		}
		catch (RuntimeException ex) {
			// The scheduler stops invoking a task that throws, which would silently halt
			// all grading. Every failure is swallowed here and retried on the next tick.
			logger.error("Grading dispatcher tick failed", ex);
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
			if (workspace != null) {
				this.executor.discardWorkspace(workspace);
			}
		}
	}

}
