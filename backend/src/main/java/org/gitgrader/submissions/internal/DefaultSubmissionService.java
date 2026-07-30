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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditService;
import org.gitgrader.submissions.NewSubmission;
import org.gitgrader.submissions.SubmissionRecorded;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.submissions.domain.Submission;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link SubmissionService}.
 */
@Service
@Transactional
public class DefaultSubmissionService implements SubmissionService {

	private static final Logger logger = LoggerFactory.getLogger(DefaultSubmissionService.class);

	private final SubmissionRepository repository;

	private final ApplicationEventPublisher events;

	private final AuditService auditService;

	private final Clock clock;

	public DefaultSubmissionService(SubmissionRepository repository, ApplicationEventPublisher events,
			AuditService auditService, Clock clock) {
		this.repository = repository;
		this.events = events;
		this.auditService = auditService;
		this.clock = clock;
	}

	@Override
	public SubmissionView record(NewSubmission details) {
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

	@Override
	public SubmissionView markStatus(UUID submissionId, SubmissionStatus status) {
		Submission submission = this.repository.findById(submissionId)
			.orElseThrow(() -> new IllegalArgumentException("No submission with id " + submissionId));
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
	public long countAttempts(UUID studentId, UUID assignmentId) {
		return this.repository.countByStudentIdAndAssignmentId(studentId, assignmentId);
	}

	private static SubmissionView toView(Submission submission) {
		return new SubmissionView(submission.id(), submission.repositoryId(), submission.repositoryPath(),
				submission.studentId(), submission.courseId(), submission.assignmentId(), submission.commitSha(),
				submission.shortCommitSha(), submission.gitRef(), submission.commitMessage(), submission.receivedAt(),
				submission.signatureStatus(), submission.signatureFingerprint(), submission.status(), submission.late(),
				submission.effectiveDueAt(), submission.rejectionReason(), submission.runtimeImageDigest());
	}

}
