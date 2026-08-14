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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.GradingResult;
import org.gitgrader.grading.GradingRunner;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.grading.internal.GradingPlanResolver.GradingPlan;
import org.gitgrader.runtimes.ReportFormat;
import org.gitgrader.runtimes.RuntimeView;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

/**
 * Tests for what {@link GradingExecutor} does when a run cannot be finished.
 *
 * <p>
 * The happy path leaves the workspace to the caller, which learns of it through the
 * returned outcome. These cover the paths where no outcome is ever returned, and the
 * materialised copy of the student's repository would otherwise have no owner.
 */
class GradingExecutorTest {

	private static final Path WORKSPACE = Path.of("/data/tmp/gitgrader-run-1");

	private static final String ONE_TEST_MANIFEST = """
			{"suiteKey":"suite-a","version":"1.0.0",
			 "tests":[{"id":"h01","name":"first","category":"cat","hint":"hint","weight":1}]}
			""";

	@TempDir
	private Path hiddenTests;

	private GradingRunner runner;

	private GradingWorkspaceFactory workspaces;

	private ReportParser reportParser;

	private GradingExecutor executor;

	private GradingRun run;

	@BeforeEach
	void setUp() throws Exception {
		writeManifest(ONE_TEST_MANIFEST);
		this.runner = mock(GradingRunner.class);
		this.workspaces = mock(GradingWorkspaceFactory.class);
		this.reportParser = mock(ReportParser.class);
		GradingPlanResolver plans = mock(GradingPlanResolver.class);
		TestResultRepository testResults = mock(TestResultRepository.class);

		GradingProperties properties = new GradingProperties("docker", "/data/grading", 2, Duration.ofSeconds(120),
				DataSize.ofMegabytes(512), 1.0, 256, false, DataSize.ofMegabytes(1), Duration.ofSeconds(20), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.Queue(Duration.ofSeconds(2), Duration.ofMinutes(15), 3, Duration.ofSeconds(30), 3,
						500, 1000, Duration.ofSeconds(30)));
		Clock clock = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC);

		this.executor = new GradingExecutor(this.runner, this.workspaces, plans, this.reportParser, testResults,
				properties, new tools.jackson.databind.ObjectMapper(), clock);

		this.run = new GradingRun(UUID.randomUUID(), 1, "PUSH", UUID.randomUUID(), "sha256:" + "a".repeat(64),
				UUID.randomUUID(), "corr-1", clock);
		when(plans.resolve(any())).thenReturn(plan());
		when(this.workspaces.materialise(any(), any())).thenReturn(WORKSPACE);
	}

	@Test
	@DisplayName("removes the workspace when the sandbox itself cannot be run")
	void discardsWorkspaceWhenTheRunnerFails() {
		when(this.runner.execute(any())).thenThrow(new IllegalStateException("engine unreachable"));

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.executor.execute(this.run));

		verify(this.workspaces).discard(WORKSPACE);
	}

	@Test
	@DisplayName("removes the workspace when the report cannot be interpreted")
	void discardsWorkspaceWhenTheReportCannotBeRead() {
		// An unreadable hidden manifest fails every submission to that assignment, so a
		// workspace stranded here is not a one-off: it repeats until the disk fills.
		when(this.runner.execute(any()))
			.thenReturn(new GradingResult(0, "1..1\nok 1 first\n", "", 10, false, false, null));
		when(this.reportParser.parse(any(), any(), any())).thenThrow(new IllegalStateException("unreadable manifest"));

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.executor.execute(this.run));

		verify(this.workspaces).discard(WORKSPACE);
	}

	@Test
	@DisplayName("keeps the workspace when the run succeeds, so the caller can use it")
	void keepsWorkspaceOnTheHappyPath() {
		when(this.runner.execute(any())).thenReturn(new GradingResult(0, "", "", 10, false, false, null));
		when(this.reportParser.parse(any(), any(), any())).thenReturn(java.util.List.of());

		GradingExecutor.Outcome outcome = this.executor.execute(this.run);

		verify(this.workspaces, never()).discard(any());
		org.assertj.core.api.Assertions.assertThat(outcome.workspace()).isEqualTo(WORKSPACE);
	}

	@Test
	@DisplayName("honours the retain-workspaces setting when discarding after a failure")
	void retainsWorkspaceForDiagnosisWhenConfiguredTo() throws Exception {
		GradingExecutor retaining = executorRetainingWorkspaces();
		doThrow(new IllegalStateException("engine unreachable")).when(this.runner).execute(any());

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> retaining.execute(this.run));

		verify(this.workspaces, never()).discard(any());
	}

	@Test
	@DisplayName("refuses to grade a suite whose manifest is missing, rather than scoring unverifiable output")
	void refusesToGradeWhenTheManifestIsMissing() throws Exception {
		Files.delete(this.hiddenTests.resolve("manifest.json"));
		when(this.runner.execute(any()))
			.thenReturn(new GradingResult(0, "1..1\nok 1 - first\n", "", 10, false, false, null));

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.executor.execute(this.run))
			.withMessageContaining("manifest.json");

		verify(this.workspaces).discard(WORKSPACE);
	}

	@Test
	@DisplayName("refuses to grade a manifest that declares no tests")
	void refusesToGradeWhenTheManifestDeclaresNoTests() throws Exception {
		writeManifest("{\"suiteKey\":\"suite-a\",\"version\":\"1.0.0\",\"tests\":[]}");
		when(this.runner.execute(any()))
			.thenReturn(new GradingResult(0, "1..1\nok 1 - first\n", "", 10, false, false, null));

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.executor.execute(this.run))
			.withMessageContaining("declares no tests");
	}

	private void writeManifest(String json) throws Exception {
		Files.writeString(this.hiddenTests.resolve("manifest.json"), json, StandardCharsets.UTF_8);
	}

	private GradingExecutor executorRetainingWorkspaces() {
		GradingPlanResolver plans = mock(GradingPlanResolver.class);
		when(plans.resolve(any())).thenReturn(plan());
		GradingProperties retaining = new GradingProperties("docker", "/data/grading", 2, Duration.ofSeconds(120),
				DataSize.ofMegabytes(512), 1.0, 256, false, DataSize.ofMegabytes(1), Duration.ofSeconds(20), true,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.Queue(Duration.ofSeconds(2), Duration.ofMinutes(15), 3, Duration.ofSeconds(30), 3,
						500, 1000, Duration.ofSeconds(30)));
		return new GradingExecutor(this.runner, this.workspaces, plans, this.reportParser,
				mock(TestResultRepository.class), retaining, new tools.jackson.databind.ObjectMapper(),
				Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC));
	}

	private GradingPlan plan() {
		UUID assignmentId = UUID.randomUUID();
		SubmissionView submission = new SubmissionView(UUID.randomUUID(), UUID.randomUUID(),
				"course-a/assignment-01/12345", UUID.randomUUID(), UUID.randomUUID(), assignmentId, "a".repeat(40),
				"aaaaaaa", "refs/heads/main", "solve", Instant.parse("2026-04-01T10:00:00Z"), SignatureVerdict.VERIFIED,
				"SHA256:key", SubmissionStatus.QUEUED, false, null, null, null);
		AssignmentView assignment = new AssignmentView(assignmentId, UUID.randomUUID(), "assignment-01",
				"Assignment 01", null, 1, AssignmentStatus.OPEN, true, null, null, "UTC", new BigDecimal("100"), 10,
				new BigDecimal("100"), false, null, UUID.randomUUID(), UUID.randomUUID(), null, null, null, null,
				false);
		RuntimeView runtime = new RuntimeView(UUID.randomUUID(), "node-24", "Node.js 24",
				"registry.example.org/runtime-node", "24.13.0", "sha256:" + "a".repeat(64), "npm ci", "npm test",
				ReportFormat.TAP, true, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"));
		return new GradingPlan(submission, "course-a/assignment-01/12345", assignment, runtime, this.hiddenTests);
	}

}
