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

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditRecord.AuditOutcome;
import org.gitgrader.audit.AuditRecord.AuditSeverity;
import org.gitgrader.audit.AuditService;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.FailureCategory;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.GradingScore;
import org.gitgrader.grading.domain.GradingJob;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionView;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Terminal grading audits must reflect committed outcomes and exclude sandbox secrets.
 */
class GradingQueueAuditTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC);

	private final GradingJobRepository jobs = mock(GradingJobRepository.class);

	private final GradingRunRepository runs = mock(GradingRunRepository.class);

	private final GradingExecutor executor = mock(GradingExecutor.class);

	private final SubmissionService submissions = mock(SubmissionService.class);

	private final AuditService audit = mock(AuditService.class);

	private final GradingRun run = new GradingRun(UUID.randomUUID(), 1, "PUSH", null, "sha256:runtime", null,
			"correlation", CLOCK);

	private GradingQueue queue;

	private GradingQueue.ClaimedJob lease;

	@BeforeEach
	void setUp() {
		GradingProperties properties = mock(GradingProperties.class);
		when(properties.queue()).thenReturn(new GradingProperties.Queue(true, Duration.ofSeconds(2),
				Duration.ofMinutes(15), 3, Duration.ofSeconds(30), 3, 500, 1000, Duration.ofSeconds(30)));
		this.queue = new GradingQueue(this.jobs, this.runs, this.executor, this.submissions, properties, CLOCK,
				this.audit);
		GradingJob job = new GradingJob(this.run.id(), this.run.submissionId(), UUID.randomUUID(), UUID.randomUUID(),
				UUID.randomUUID(), 1, CLOCK);
		job.claim("worker", Duration.ofMinutes(15), CLOCK);
		this.lease = new GradingQueue.ClaimedJob(job.id(), "worker", job.leaseGeneration(), job.claimExpiresAt());
		when(this.jobs.lockRunningLease(any(), any(), anyLong(), any())).thenReturn(Optional.of(job.id()));
		when(this.jobs.findById(job.id())).thenReturn(Optional.of(job));
		when(this.runs.findById(this.run.id())).thenReturn(Optional.of(this.run));
		when(this.submissions.markStatus(any(), any())).thenAnswer(invocation -> {
			SubmissionView submission = mock(SubmissionView.class);
			when(submission.courseId()).thenReturn(job.courseId());
			when(submission.status()).thenReturn(invocation.getArgument(1));
			return submission;
		});
		TransactionSynchronizationManager.initSynchronization();
	}

	@AfterEach
	void clearTransaction() {
		TransactionSynchronizationManager.clearSynchronization();
	}

	@Test
	void infrastructureOutcomeIsAuditedOnlyAfterCommitWithoutRawFailureText() {
		doAnswer(invocation -> {
			this.run.fail(FailureCategory.INFRASTRUCTURE_ERROR, "secret sandbox output", GradingRunStatus.TIMEOUT,
					CLOCK);
			return null;
		}).when(this.executor).persist(any(), any());
		assertThat(this.queue.recordSuccess(this.lease, this.run.id(), mock(GradingExecutor.Outcome.class))).isTrue();
		verifyNoInteractions(this.audit);
		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		AuditRecord record = recorded();
		assertThat(record.type()).isEqualTo(AuditEventType.GRADING_COMPLETED);
		assertThat(record.outcome()).isEqualTo(AuditOutcome.FAILURE);
		assertThat(record.severity()).isEqualTo(AuditSeverity.WARNING);
		assertThat(record.subjectId()).isEqualTo(this.run.submissionId().toString());
		assertThat(record.correlationId()).isEqualTo("correlation");
		assertThat(record.detail()).containsEntry("status", "INFRASTRUCTURE_ERROR")
			.containsEntry("failureCategory", FailureCategory.INFRASTRUCTURE_ERROR)
			.containsEntry("runtimeImageDigest", "sha256:runtime")
			.containsEntry("gradingStatus", "TIMEOUT");
		assertThat(record.detail().toString()).doesNotContain("secret sandbox output");
	}

	@Test
	void exhaustedWorkerFailuresProduceAnAuditAfterCommit() {
		assertThat(this.queue.recordFailure(this.lease, this.run.id(), new IllegalStateException("secret"))).isTrue();
		verifyNoInteractions(this.audit);
		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		assertThat(recorded().detail()).containsEntry("status", "INFRASTRUCTURE_ERROR");
	}

	@Test
	void rolledBackOutcomeDoesNotProduceAnAudit() {
		this.queue.recordFailure(this.lease, this.run.id(), new IllegalStateException("failed"));
		TransactionSynchronizationManager.getSynchronizations()
			.forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
		verifyNoInteractions(this.audit);
	}

	@Test
	void staleWorkerCannotProduceAnAudit() {
		when(this.jobs.lockRunningLease(any(), any(), anyLong(), any())).thenReturn(Optional.empty());
		assertThat(this.queue.recordFailure(this.lease, this.run.id(), new IllegalStateException("stale"))).isFalse();
		assertThat(this.queue.recordSuccess(this.lease, this.run.id(), mock(GradingExecutor.Outcome.class))).isFalse();
		assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
		verifyNoInteractions(this.audit);
	}

	@ParameterizedTest
	@ValueSource(booleans = { true, false })
	void completedTestsAreSuccessfulGradingActionsEvenWhenStudentFails(boolean passed) {
		doAnswer(invocation -> {
			this.run.markRunning(CLOCK);
			this.run.complete(
					new GradingScore(1, passed ? 1 : 0, passed ? 0 : 1, 0, 0,
							passed ? BigDecimal.valueOf(100) : BigDecimal.ZERO, BigDecimal.ZERO, passed),
					0, 100, CLOCK);
			return null;
		}).when(this.executor).persist(any(), any());
		this.queue.recordSuccess(this.lease, this.run.id(), mock(GradingExecutor.Outcome.class));
		TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
		AuditRecord record = recorded();
		assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
		assertThat(record.detail()).containsEntry("status", passed ? "PASSED" : "FAILED");
	}

	@Test
	void scheduledRetryIsNotReportedAsTerminalCompletion() {
		GradingJob retrying = mock(GradingJob.class);
		when(retrying.recordFailure(any(), any(), any())).thenReturn(true);
		when(this.jobs.findById(this.lease.jobId())).thenReturn(Optional.of(retrying));
		this.queue.recordFailure(this.lease, this.run.id(), new IllegalStateException("retry"));
		assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
		verifyNoInteractions(this.audit);
	}

	@Test
	void unavailableAuditStorageDoesNotBreakCommittedGrading() {
		this.queue.recordFailure(this.lease, this.run.id(), new IllegalStateException("failed"));
		doThrow(new IllegalStateException("audit unavailable")).when(this.audit).record(any());
		assertThatCode(() -> TransactionSynchronizationManager.getSynchronizations()
			.forEach(TransactionSynchronization::afterCommit)).doesNotThrowAnyException();
	}

	private AuditRecord recorded() {
		ArgumentCaptor<AuditRecord> record = ArgumentCaptor.forClass(AuditRecord.class);
		verify(this.audit).record(record.capture());
		return record.getValue();
	}

}
