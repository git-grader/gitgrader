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

package org.gitgrader.submissions.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Predicate;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditRecord.ActorType;
import org.gitgrader.audit.AuditRecord.AuditSeverity;
import org.gitgrader.audit.AuditService;
import org.gitgrader.configuration.SecurityProperties;
import org.gitgrader.configuration.SecurityProperties.RateLimits;
import org.gitgrader.submissions.NewSubmission;
import org.gitgrader.submissions.SubmissionRecorded;
import org.gitgrader.submissions.SubmissionRefusedException;
import org.gitgrader.submissions.SubmissionAssessmentView;
import org.gitgrader.submissions.SubmissionRefusedException.Reason;
import org.gitgrader.submissions.SubmissionSearch;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.submissions.domain.Submission;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link SubmissionService}.
 */
@Service
@Transactional
public class DefaultSubmissionService implements SubmissionService {

	private static final Logger logger = LoggerFactory.getLogger(DefaultSubmissionService.class);

	/** The rolling window every push allowance is measured over. */
	private static final Duration WINDOW = Duration.ofHours(1);

	/** Hash characters shown to a student, matching git's default abbreviation. */
	private static final int SHORT_SHA_LENGTH = 7;

	/** Shared with the grading module; tags stay low cardinality for the same reason. */
	private static final String THROTTLE_COUNTER = "gitgrader.throttle";

	/** Rotation applied to one half of the lock key so the two ids do not cancel out. */
	private static final int KEY_HALF_BITS = 32;

	private final SubmissionRepository repository;

	private final ApplicationEventPublisher events;

	private final AuditService auditService;

	private final SecurityProperties securityProperties;

	private final MeterRegistry meters;

	private final Clock clock;

	public DefaultSubmissionService(SubmissionRepository repository, ApplicationEventPublisher events,
			AuditService auditService, SecurityProperties securityProperties, MeterRegistry meters, Clock clock) {
		this.repository = repository;
		this.events = events;
		this.auditService = auditService;
		this.securityProperties = securityProperties;
		this.meters = meters;
		this.clock = clock;
	}

	@Override
	public SubmissionView record(NewSubmission details) {
		admit(details);
		Submission saved = this.repository.save(new Submission(details, this.clock));
		boolean gradable = saved.status() != SubmissionStatus.REJECTED;

		this.auditService
			.record(AuditRecord.of(gradable ? AuditEventType.SUBMISSION_RECEIVED : AuditEventType.SUBMISSION_REJECTED)
				.subject("Submission", saved.id().toString())
				.course(saved.courseId())
				.with("studentId", saved.studentId().toString())
				.with("assignmentId", saved.assignmentId().toString())
				.with("commit", saved.shortCommitSha())
				.with("signature", saved.signatureStatus().name())
				.with("late", saved.late())
				.with("rejectionReason", saved.rejectionReason())
				.build());

		// Published inside this transaction. Spring Modulith writes it to the publication
		// registry alongside the row, so a crash before grading finishes leaves the work
		// recoverable rather than lost.
		this.events.publishEvent(new SubmissionRecorded(saved.id(), saved.studentId(), saved.courseId(),
				saved.assignmentId(), saved.commitSha(), saved.receivedAt(), gradable));

		logger.info("Recorded submission {} for student {} on assignment {} (commit {}, signature {})", saved.id(),
				saved.studentId(), saved.assignmentId(), saved.shortCommitSha(), saved.signatureStatus());
		return toView(saved);
	}

	/**
	 * Decides whether a well formed push may become a submission.
	 *
	 * <p>
	 * Runs inside the recording transaction and behind a per-student advisory lock, so
	 * the count a decision is based on cannot change between reading it and inserting the
	 * row. Counting in the database rather than in a bucket in memory is what makes the
	 * limit survive a restart and hold across a second instance.
	 *
	 * <p>
	 * A refused push is not written. That is a deliberate departure from the usual rule
	 * that every attempt is recorded: the reason a push is refused here is that there are
	 * already too many rows, so recording the refusal would defeat the limit it enforces.
	 * The audit trail carries the refusal instead.
	 * @param details the push being admitted
	 * @throws SubmissionRefusedException when a rule refuses it
	 */
	private void admit(NewSubmission details) {
		RateLimits limits = this.securityProperties.rateLimits();
		this.repository.lockForAdmission(admissionKey(details.repositoryId()));

		if (this.repository.existsByRepositoryIdAndCommitSha(details.repositoryId(), details.commitSha())) {
			refuseDuplicate(details);
		}

		Instant since = Instant.now(this.clock).minus(WINDOW);
		if (this.repository.countByStudentIdAndAssignmentIdAndReceivedAtAfter(details.studentId(),
				details.assignmentId(), since) >= limits.submissionsPerHourPerAssignment()) {
			refuse(details, Reason.ASSIGNMENT_RATE_LIMIT, "security.rate-limits.submissions-per-hour-per-assignment",
					"You have submitted this assignment " + limits.submissionsPerHourPerAssignment()
							+ " times in the last hour, which is the limit. Wait before pushing again.");
		}
		if (this.repository.countByStudentIdAndReceivedAtAfter(details.studentId(), since) >= limits
			.submissionsPerHourPerStudent()) {
			refuse(details, Reason.STUDENT_RATE_LIMIT, "security.rate-limits.submissions-per-hour-per-student",
					"You have made " + limits.submissionsPerHourPerStudent()
							+ " submissions in the last hour, which is the limit. Wait before pushing again.");
		}
	}

	private void refuseDuplicate(NewSubmission details) {
		refuse(details, Reason.DUPLICATE_COMMIT, "submissions.duplicate-commit",
				"Commit " + shortSha(details.commitSha()) + " has already been submitted for this assignment. "
						+ "Push a new commit to be graded again.");
	}

	private void refuse(NewSubmission details, Reason reason, String limit, String message) {
		this.auditService.record(AuditRecord.of(AuditEventType.RATE_LIMIT_TRIGGERED)
			.severity(AuditSeverity.WARNING)
			.denied()
			.actor(ActorType.STUDENT, details.studentId().toString(), null)
			.subject("Assignment", details.assignmentId().toString())
			.course(details.courseId())
			.with("limit", limit)
			.with("decision", reason.name())
			.with("commit", shortSha(details.commitSha()))
			.build());
		this.meters.counter(THROTTLE_COUNTER, "limit", limit, "decision", reason.name()).increment();
		logger.info("Refused push from student {} on assignment {}: {}", details.studentId(), details.assignmentId(),
				reason);
		throw new SubmissionRefusedException(reason, message);
	}

	/**
	 * Derives the advisory lock key that serialises admission for one student and
	 * assignment.
	 *
	 * <p>
	 * A collision between two unrelated pairs only makes them take turns. It cannot admit
	 * a push that should have been refused, because every check runs after the lock is
	 * held.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return a stable key for {@code pg_advisory_xact_lock}
	 */
	private static long admissionKey(UUID repositoryId) {
		return repositoryId.getMostSignificantBits()
				^ Long.rotateLeft(repositoryId.getLeastSignificantBits(), KEY_HALF_BITS);
	}

	private static String shortSha(String commitSha) {
		return commitSha.length() > SHORT_SHA_LENGTH ? commitSha.substring(0, SHORT_SHA_LENGTH) : commitSha;
	}

	@Override
	public SubmissionView markStatus(UUID submissionId, SubmissionStatus status) {
		Submission submission = this.repository.findByIdForStatusUpdate(submissionId)
			.orElseThrow(() -> new EntityNotFoundException("No submission with id " + submissionId));
		submission.updateStatus(status);
		return toView(this.repository.save(submission));
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<SubmissionView> findById(UUID submissionId) {
		return this.repository.findById(submissionId).map(DefaultSubmissionService::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SubmissionView> findHistory(UUID studentId, UUID assignmentId) {
		return this.repository.findByStudentIdAndAssignmentIdOrderByReceivedAtDesc(studentId, assignmentId)
			.stream()
			.map(DefaultSubmissionService::toView)
			.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<SubmissionView> findLatest(UUID studentId, UUID assignmentId) {
		return this.repository.findFirstByStudentIdAndAssignmentIdOrderByReceivedAtDesc(studentId, assignmentId)
			.map(DefaultSubmissionService::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<SubmissionView> findLatestForStudent(UUID studentId) {
		return this.repository.findFirstByStudentIdOrderByReceivedAtDesc(studentId)
			.map(DefaultSubmissionService::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<SubmissionView> findByCourse(UUID courseId, Pageable pageable) {
		return this.repository.findByCourseId(courseId, pageable).map(DefaultSubmissionService::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<SubmissionView> findAll(Pageable pageable) {
		return this.repository.findAll(pageable).map(DefaultSubmissionService::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<SubmissionView> search(SubmissionSearch search, Pageable pageable) {
		return this.repository.findAll(specification(search), pageable).map(DefaultSubmissionService::toView);
	}

	private static Specification<Submission> specification(SubmissionSearch search) {
		return (root, query, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			equalIfPresent(builder, predicates, root.get("courseId"), search.courseId());
			equalIfPresent(builder, predicates, root.get("assignmentId"), search.assignmentId());
			equalIfPresent(builder, predicates, root.get("studentId"), search.studentId());
			equalIfPresent(builder, predicates, root.get("status"), search.status());
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

	private static void equalIfPresent(CriteriaBuilder builder, List<Predicate> predicates, Path<?> attribute,
			@Nullable Object value) {
		if (value != null) {
			predicates.add(builder.equal(attribute, value));
		}
	}

	@Override
	@Transactional(readOnly = true)
	public long countByStatus(SubmissionStatus status) {
		return this.repository.countByStatus(status);
	}

	@Override
	@Transactional(readOnly = true)
	public long countAttempts(UUID studentId, UUID assignmentId) {
		return this.repository.countByStudentIdAndAssignmentId(studentId, assignmentId);
	}

	@Override
	@Transactional(readOnly = true)
	public List<SubmissionAssessmentView> findAssessments(UUID courseId, Collection<UUID> assignmentIds) {
		return assignmentIds.isEmpty() ? List.of() : this.repository.findAssessments(courseId, assignmentIds);
	}

	private static SubmissionView toView(Submission submission) {
		return new SubmissionView(submission.id(), submission.repositoryId(), submission.repositoryPath(),
				submission.studentId(), submission.courseId(), submission.assignmentId(), submission.commitSha(),
				submission.shortCommitSha(), submission.gitRef(), submission.commitMessage(), submission.receivedAt(),
				submission.signatureStatus(), submission.signatureFingerprint(), submission.status(), submission.late(),
				submission.effectiveDueAt(), submission.rejectionReason(), submission.runtimeImageDigest());
	}

}
