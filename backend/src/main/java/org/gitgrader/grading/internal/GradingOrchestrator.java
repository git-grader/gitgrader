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
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.domain.GradingJob;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.submissions.SubmissionRecorded;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Turns an accepted push into queued grading work.
 *
 * <p>
 * Listens for {@link SubmissionRecorded} rather than being called directly by the Git
 * module. That is what keeps the dependency pointing one way: {@code submissions}
 * announces a fact and does not wait for grading, so a slow or failing sandbox can never
 * hold up a push.
 *
 * <p>
 * {@link ApplicationModuleListener} runs the handler in its own transaction after the
 * publishing one commits, and records the publication so that a crash between the two
 * leaves the work recoverable on restart. Without the persistent registry, a restart at
 * the wrong moment would lose the submission's grading entirely.
 */
@Component
public class GradingOrchestrator {

	private static final Logger logger = LoggerFactory.getLogger(GradingOrchestrator.class);

	private final GradingRunRepository runs;

	private final GradingJobRepository jobs;

	private final AssignmentCatalog assignments;

	private final SubmissionService submissions;

	private final GradingProperties properties;

	private final Clock clock;

	public GradingOrchestrator(GradingRunRepository runs, GradingJobRepository jobs, AssignmentCatalog assignments,
			SubmissionService submissions, GradingProperties properties, Clock clock) {
		this.runs = runs;
		this.jobs = jobs;
		this.assignments = assignments;
		this.submissions = submissions;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * Queues a grading run for a newly recorded submission.
	 * @param event the submission that was just recorded
	 */
	@ApplicationModuleListener
	public void onSubmissionRecorded(SubmissionRecorded event) {
		if (!event.gradable()) {
			// A refused push is recorded for the audit trail but must never reach a
			// sandbox: there is nothing to grade and running it would waste a worker.
			logger.debug("Submission {} is not gradable; no grading run queued", event.submissionId());
			return;
		}
		enqueue(event.submissionId(), event.assignmentId(), "PUSH");
	}

	/**
	 * Queues a grading run, creating the next attempt for the submission.
	 *
	 * <p>
	 * Also used for a manual re-grade, which is why the attempt number comes from the
	 * database rather than being assumed to be one.
	 * @param submissionId the submission to grade
	 * @param assignmentId the assignment it answers
	 * @param trigger what caused this run
	 * @return the queued run
	 */
	@Transactional
	public GradingRun enqueue(UUID submissionId, UUID assignmentId, String trigger) {
		Optional<AssignmentView> assignment = this.assignments.findAssignment(assignmentId);
		String correlationId = UUID.randomUUID().toString();

		GradingRun run = this.runs.save(new GradingRun(submissionId, this.runs.nextAttempt(submissionId), trigger,
				assignment.map(AssignmentView::runtimeId).orElse(null), null,
				assignment.map(AssignmentView::testSuiteVersionId).orElse(null), correlationId, this.clock));

		this.jobs.save(new GradingJob(run.id(), submissionId, this.properties.queue().maxAttempts(), this.clock));
		this.submissions.markStatus(submissionId, SubmissionStatus.QUEUED);

		logger.info("Queued grading run {} (attempt {}) for submission {} [correlationId={}]", run.id(), run.attempt(),
				submissionId, correlationId);
		return run;
	}

}
