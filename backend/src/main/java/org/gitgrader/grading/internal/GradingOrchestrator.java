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
import java.util.EnumSet;
import java.util.Set;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditRecord.ActorType;
import org.gitgrader.audit.AuditRecord.AuditSeverity;
import org.gitgrader.audit.AuditService;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.GradingJobStatus;
import org.gitgrader.grading.domain.GradingJob;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.submissions.SubmissionRecorded;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.jspecify.annotations.Nullable;
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

	/** Rotation applied to one half of the lock key so the two ids do not cancel out. */
	private static final int KEY_HALF_BITS = 32;

	/** A job in any of these is either running or about to be. */
	private static final Set<GradingJobStatus> ACTIVE_JOB_STATUSES = EnumSet.of(GradingJobStatus.CLAIMED,
			GradingJobStatus.RUNNING);

	/** The trigger recorded for the run a push itself queues. */
	private static final String PUSH_TRIGGER = "PUSH";

	/**
	 * Counter for every decision that withheld grading.
	 *
	 * <p>
	 * Tagged only with the limit and the decision. Tagging with student, assignment or
	 * course would give the series unbounded cardinality, which is how a metrics backend
	 * is brought down by the very traffic these limits exist to survive; the audit trail
	 * is where a specific student's throttling is looked up.
	 */
	private static final String THROTTLE_COUNTER = "gitgrader.throttle";

	private final GradingRunRepository runs;

	private final GradingJobRepository jobs;

	private final AssignmentCatalog assignments;

	private final SubmissionService submissions;

	private final GradingProperties properties;

	private final AuditService auditService;

	private final MeterRegistry meters;

	private final Clock clock;

	public GradingOrchestrator(GradingRunRepository runs, GradingJobRepository jobs, AssignmentCatalog assignments,
			SubmissionService submissions, GradingProperties properties, AuditService auditService,
			MeterRegistry meters, Clock clock) {
		this.runs = runs;
		this.jobs = jobs;
		this.assignments = assignments;
		this.submissions = submissions;
		this.properties = properties;
		this.auditService = auditService;
		this.meters = meters;
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
		enqueue(event.submissionId(), event.studentId(), event.courseId(), event.assignmentId(), PUSH_TRIGGER);
	}

	/**
	 * Queues a grading run, superseding any unstarted run this submission replaces.
	 *
	 * <p>
	 * Only the newest unstarted submission for a student and assignment is worth grading.
	 * A student pushing repeatedly used to add a sandbox run per push, all of which sat
	 * in front of the rest of the course; now the older queued run is withdrawn and the
	 * newest one takes its place. Every push is still recorded - the attempt history is
	 * unchanged - but superseded work is marked {@code CANCELLED} rather than executed.
	 *
	 * <p>
	 * Work already claimed by a worker is never withdrawn. Cancelling a running sandbox
	 * would abandon its container and workspace, and the student would lose a result they
	 * were already waiting for.
	 * @param submissionId the submission to grade
	 * @param studentId the student who pushed
	 * @param courseId the course the assignment belongs to
	 * @param assignmentId the assignment it answers
	 * @param trigger what caused this run
	 * @return the queued run, or empty when a queue ceiling refused it
	 */
	@Transactional
	public Optional<GradingRun> enqueue(UUID submissionId, UUID studentId, UUID courseId, UUID assignmentId,
			String trigger) {
		this.jobs.lockForEnqueue(lockKey(studentId, assignmentId));

		if (PUSH_TRIGGER.equals(trigger)) {
			// Spring Modulith replays a publication it never saw marked complete, which
			// is what happens when the process dies between the listener committing and
			// that mark being written. Without this the replay ran the whole path again:
			// the push it had already graded superseded its own queued job, took a second
			// sandbox, and replaced the result the student had been shown. The partial
			// unique index behind this lookup is the guarantee; the lock above is what
			// makes checking before inserting safe.
			Optional<GradingRun> alreadyQueued = this.runs.findBySubmissionIdAndTrigger(submissionId, PUSH_TRIGGER);
			if (alreadyQueued.isPresent()) {
				logger.debug("Submission {} already has a run for its push; not queueing another", submissionId);
				return alreadyQueued;
			}
		}

		// A second run for a submission that is already being graded gives its status
		// projection two writers, and the one that finishes last wins: an older attempt
		// could publish over a newer result, and the newer one then failed its own status
		// transition. Superseding below withdraws work that has not started; work a
		// worker
		// already holds is never withdrawn, so the only safe answer is to refuse.
		if (this.jobs.existsBySubmissionIdAndStatusIn(submissionId, ACTIVE_JOB_STATUSES)) {
			logger.info("Submission {} is already being graded; not queueing another run", submissionId);
			return Optional.empty();
		}

		supersedePending(studentId, assignmentId, submissionId);

		String exceeded = firstExceededCeiling(studentId, courseId);
		if (exceeded != null) {
			refuse(submissionId, studentId, courseId, assignmentId, exceeded);
			return Optional.empty();
		}

		Optional<AssignmentView> assignment = this.assignments.findAssignment(assignmentId);
		String correlationId = UUID.randomUUID().toString();

		GradingRun run = this.runs.save(new GradingRun(submissionId, this.runs.nextAttempt(submissionId), trigger,
				assignment.map(AssignmentView::runtimeId).orElse(null), null,
				assignment.map(AssignmentView::testSuiteVersionId).orElse(null), correlationId, this.clock));

		this.jobs.save(new GradingJob(run.id(), submissionId, studentId, courseId, assignmentId,
				this.properties.queue().maxAttempts(), this.clock));
		this.submissions.markStatus(submissionId, SubmissionStatus.QUEUED);

		logger.info("Queued grading run {} (attempt {}) for submission {} [correlationId={}]", run.id(), run.attempt(),
				submissionId, correlationId);
		return Optional.of(run);
	}

	private void supersedePending(UUID studentId, UUID assignmentId, UUID replacementId) {
		this.jobs.findByStudentIdAndAssignmentIdAndStatus(studentId, assignmentId, GradingJobStatus.PENDING)
			.ifPresent((stale) -> {
				stale.cancel(this.clock);
				this.runs.findById(stale.gradingRunId()).ifPresent((run) -> run.cancel(this.clock));
				this.submissions.markStatus(stale.submissionId(), SubmissionStatus.CANCELLED);

				this.auditService.record(AuditRecord.of(AuditEventType.RATE_LIMIT_TRIGGERED)
					.severity(AuditSeverity.INFO)
					.actor(ActorType.STUDENT, studentId.toString(), null)
					.subject("Submission", stale.submissionId().toString())
					.course(stale.courseId())
					.with("limit", "grading.pending-per-assignment")
					.with("decision", "SUPERSEDE_PENDING")
					.with("assignmentId", assignmentId.toString())
					.with("supersededBy", replacementId.toString())
					.build());
				count("grading.pending-per-assignment", "SUPERSEDE_PENDING");

				logger.info("Superseded queued grading for submission {}; submission {} replaces it",
						stale.submissionId(), replacementId);
			});
	}

	private @Nullable String firstExceededCeiling(UUID studentId, UUID courseId) {
		GradingProperties.Queue queue = this.properties.queue();
		if (this.jobs.countByStudentIdAndCourseIdAndStatus(studentId, courseId, GradingJobStatus.PENDING) >= queue
			.maxPendingPerStudentPerCourse()) {
			return "grading.queue.max-pending-per-student-per-course";
		}
		if (this.jobs.countByCourseIdAndStatus(courseId, GradingJobStatus.PENDING) >= queue.maxPendingPerCourse()) {
			return "grading.queue.max-pending-per-course";
		}
		if (this.jobs.countByStatus(GradingJobStatus.PENDING) >= queue.maxPendingGlobal()) {
			return "grading.queue.max-pending-global";
		}
		return null;
	}

	private void refuse(UUID submissionId, UUID studentId, UUID courseId, UUID assignmentId, String limit) {
		this.submissions.markStatus(submissionId, SubmissionStatus.CANCELLED);
		this.auditService.record(AuditRecord.of(AuditEventType.RATE_LIMIT_TRIGGERED)
			.severity(AuditSeverity.WARNING)
			.denied()
			.actor(ActorType.STUDENT, studentId.toString(), null)
			.subject("Submission", submissionId.toString())
			.course(courseId)
			.with("limit", limit)
			.with("decision", "QUEUE_CAP")
			.with("assignmentId", assignmentId.toString())
			.build());
		count(limit, "QUEUE_CAP");
		logger.warn("Refused to queue submission {}: {} reached", submissionId, limit);
	}

	private void count(String limit, String decision) {
		this.meters.counter(THROTTLE_COUNTER, "limit", limit, "decision", decision).increment();
	}

	/**
	 * Derives the advisory lock key that serialises enqueues for one student and
	 * assignment.
	 *
	 * <p>
	 * A collision between two unrelated pairs only makes them take turns, which costs a
	 * little contention and nothing else: the partial unique index, not this key, is what
	 * guarantees a student never ends up with two queued runs for one assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return a stable key for {@code pg_advisory_xact_lock}
	 */
	private static long lockKey(UUID studentId, UUID assignmentId) {
		long student = studentId.getMostSignificantBits() ^ studentId.getLeastSignificantBits();
		long assignment = assignmentId.getMostSignificantBits() ^ assignmentId.getLeastSignificantBits();
		return student ^ Long.rotateLeft(assignment, KEY_HALF_BITS);
	}

}
