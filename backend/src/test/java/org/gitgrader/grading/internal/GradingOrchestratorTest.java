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
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.audit.AuditService;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.domain.GradingJob;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.submissions.SubmissionRecorded;
import org.gitgrader.submissions.SubmissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers what queueing does when it is asked for the same push twice.
 *
 * <p>
 * Spring Modulith replays an event publication it never saw marked complete, which is
 * what a process dying between the listener's commit and that mark produces. The replay
 * is indistinguishable from the original, so the guard has to be here.
 */
class GradingOrchestratorTest {

	private static final UUID SUBMISSION = UUID.randomUUID();

	private static final UUID STUDENT = UUID.randomUUID();

	private static final UUID COURSE = UUID.randomUUID();

	private static final UUID ASSIGNMENT = UUID.randomUUID();

	private GradingRunRepository runs;

	private GradingJobRepository jobs;

	private SubmissionService submissions;

	private GradingOrchestrator orchestrator;

	@BeforeEach
	void setUp() {
		this.runs = mock(GradingRunRepository.class);
		this.jobs = mock(GradingJobRepository.class);
		this.submissions = mock(SubmissionService.class);
		Clock clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC);

		GradingProperties properties = new GradingProperties("docker", 2, Duration.ofSeconds(120),
				DataSize.ofMegabytes(512), 1.0, 256, false, DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.Queue(Duration.ofSeconds(2), Duration.ofMinutes(15), 3, Duration.ofSeconds(30), 3,
						500, 1000, Duration.ofSeconds(30)));

		this.orchestrator = new GradingOrchestrator(this.runs, this.jobs, mock(AssignmentCatalog.class),
				this.submissions, properties, mock(AuditService.class), new SimpleMeterRegistry(), clock);
	}

	@Test
	@DisplayName("queues a run for a push that has not been queued before")
	void queuesTheFirstTimeAPushIsRecorded() {
		when(this.runs.findBySubmissionIdAndTrigger(SUBMISSION, "PUSH")).thenReturn(Optional.empty());
		when(this.runs.nextAttempt(SUBMISSION)).thenReturn(1);
		when(this.runs.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));

		this.orchestrator.onSubmissionRecorded(recorded());

		verify(this.runs).save(any(GradingRun.class));
		verify(this.jobs).save(any(GradingJob.class));
	}

	@Test
	@DisplayName("does not queue a second run when the same push is delivered again")
	void doesNotQueueASecondRunForAReplayedPush() {
		GradingRun existing = new GradingRun(SUBMISSION, 1, "PUSH", null, null, null, "corr-1",
				Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC));
		when(this.runs.findBySubmissionIdAndTrigger(SUBMISSION, "PUSH")).thenReturn(Optional.of(existing));

		Optional<GradingRun> queued = this.orchestrator.enqueue(SUBMISSION, STUDENT, COURSE, ASSIGNMENT, "PUSH");

		assertThat(queued).contains(existing);
		verify(this.runs, never()).save(any());
		verify(this.jobs, never()).save(any());
		// The replay must not supersede the job its own first delivery queued, which is
		// how one push ended up cancelling itself and taking a second sandbox.
		verify(this.jobs, never()).findByStudentIdAndAssignmentIdAndStatus(any(), any(), any());
		verify(this.submissions, never()).markStatus(any(), any());
	}

	@Test
	@DisplayName("still queues a regrade for a submission that a push already graded")
	void queuesARegradeEvenWhenThePushRunExists() {
		when(this.runs.findBySubmissionIdAndTrigger(any(), any())).thenReturn(Optional.empty());
		when(this.runs.nextAttempt(SUBMISSION)).thenReturn(2);
		when(this.runs.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));

		Optional<GradingRun> queued = this.orchestrator.enqueue(SUBMISSION, STUDENT, COURSE, ASSIGNMENT,
				"MANUAL_RETRY");

		assertThat(queued).isPresent();
		verify(this.runs).save(any(GradingRun.class));
	}

	private static SubmissionRecorded recorded() {
		return new SubmissionRecorded(SUBMISSION, STUDENT, COURSE, ASSIGNMENT, "a".repeat(40),
				Instant.parse("2026-04-01T10:00:00Z"), true);
	}

}
