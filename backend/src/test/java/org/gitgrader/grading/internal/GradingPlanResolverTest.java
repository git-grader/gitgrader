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
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.internal.GradingPlanResolver.GradingPlan;
import org.gitgrader.runtimes.ReportFormat;
import org.gitgrader.runtimes.RuntimeCatalog;
import org.gitgrader.runtimes.RuntimeView;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.templates.TestSuiteCatalog;
import org.gitgrader.templates.TestSuiteVersionView;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link GradingPlanResolver}.
 *
 * <p>
 * Every path here ends in either a complete plan or a loud failure. That is the point: a
 * run whose runtime or hidden suite has gone missing cannot produce a defensible grade,
 * so it must become an infrastructure error rather than quietly score zero.
 */
class GradingPlanResolverTest {

	private static final UUID SUBMISSION = UUID.randomUUID();

	private static final UUID ASSIGNMENT = UUID.randomUUID();

	private static final UUID RUNTIME = UUID.randomUUID();

	private static final UUID SUITE = UUID.randomUUID();

	private SubmissionService submissions;

	private AssignmentCatalog assignments;

	private RuntimeCatalog runtimes;

	private TestSuiteCatalog testSuites;

	private GradingPlanResolver resolver;

	@BeforeEach
	void setUp() {
		this.submissions = mock(SubmissionService.class);
		this.assignments = mock(AssignmentCatalog.class);
		this.runtimes = mock(RuntimeCatalog.class);
		this.testSuites = mock(TestSuiteCatalog.class);
		StorageProperties storage = new StorageProperties("/data/repositories", "/data/templates", "/data/tests",
				"/data/artifacts", "/data/tmp");
		this.resolver = new GradingPlanResolver(this.submissions, this.assignments, this.runtimes, this.testSuites,
				storage);
	}

	@Test
	@DisplayName("resolves everything a run needs")
	void resolvesCompletePlan() {
		givenSubmission("course-a/assignment-01/12345");
		givenAssignment(RUNTIME, SUITE);
		givenRuntime();
		givenSuite("suite-a/1.0.0");

		GradingPlan plan = this.resolver.resolve(SUBMISSION);

		assertThat(plan.repositoryPath()).isEqualTo("course-a/assignment-01/12345");
		assertThat(plan.runtime().id()).isEqualTo(RUNTIME);
		// Resolved under the configured tests root, never anywhere a student can reach.
		assertThat(plan.hiddenTests().toString()).endsWith("/data/tests/suite-a/1.0.0");
	}

	@Test
	@DisplayName("refuses a submission that never recorded where its objects live")
	void refusesSubmissionWithoutRepositoryPath() {
		givenSubmission(null);

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.resolver.resolve(SUBMISSION))
			.withMessageContaining("cannot be reproduced");
	}

	@Test
	@DisplayName("refuses an assignment with no runtime")
	void refusesMissingRuntime() {
		givenSubmission("course-a/assignment-01/12345");
		givenAssignment(null, SUITE);

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.resolver.resolve(SUBMISSION))
			.withMessageContaining("no usable runtime");
	}

	@Test
	@DisplayName("refuses an assignment whose runtime was deleted")
	void refusesDeletedRuntime() {
		givenSubmission("course-a/assignment-01/12345");
		givenAssignment(RUNTIME, SUITE);
		when(this.runtimes.findRuntime(any())).thenReturn(Optional.empty());

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.resolver.resolve(SUBMISSION))
			.withMessageContaining("no usable runtime");
	}

	@Test
	@DisplayName("refuses an assignment with no published test suite")
	void refusesMissingTestSuite() {
		givenSubmission("course-a/assignment-01/12345");
		givenAssignment(RUNTIME, null);
		givenRuntime();

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.resolver.resolve(SUBMISSION))
			.withMessageContaining("no published test suite");
	}

	@Test
	@DisplayName("refuses a submission that no longer exists")
	void refusesMissingSubmission() {
		when(this.submissions.findById(any())).thenReturn(Optional.empty());

		assertThatExceptionOfType(IllegalStateException.class).isThrownBy(() -> this.resolver.resolve(SUBMISSION))
			.withMessageContaining("is gone");
	}

	@Test
	@DisplayName("refuses a hidden suite path that would escape the tests root")
	void refusesTraversalInSuitePath() {
		givenSubmission("course-a/assignment-01/12345");
		givenAssignment(RUNTIME, SUITE);
		givenRuntime();
		// A stored path is instructor-supplied. If it could walk out of the tests root it
		// would let a suite be read from anywhere on the host.
		givenSuite("../../etc");

		assertThatExceptionOfType(IllegalArgumentException.class).isThrownBy(() -> this.resolver.resolve(SUBMISSION))
			.withMessageContaining("escapes");
	}

	private void givenSubmission(@Nullable String repositoryPath) {
		when(this.submissions.findById(any())).thenReturn(Optional.of(new SubmissionView(SUBMISSION, UUID.randomUUID(),
				repositoryPath, UUID.randomUUID(), UUID.randomUUID(), ASSIGNMENT, "a".repeat(40), "aaaaaaa",
				"refs/heads/main", "solve", Instant.parse("2026-04-01T10:00:00Z"), SignatureVerdict.VERIFIED,
				"SHA256:key", SubmissionStatus.QUEUED, false, null, null, null)));
	}

	private void givenAssignment(@Nullable UUID runtimeId, @Nullable UUID suiteId) {
		when(this.assignments.findAssignment(any()))
			.thenReturn(Optional.of(new AssignmentView(ASSIGNMENT, UUID.randomUUID(), "assignment-01", "Assignment 01",
					null, 1, AssignmentStatus.OPEN, true, null, null, "UTC", new BigDecimal("100"), 10,
					new BigDecimal("100"), false, null, suiteId, runtimeId, null, null, null, null, false)));
	}

	private void givenRuntime() {
		when(this.runtimes.findRuntime(any())).thenReturn(Optional.of(new RuntimeView(RUNTIME, "node-24", "Node.js 24",
				"registry.example.org/runtime-node", "24.13.0", "sha256:" + "a".repeat(64), "npm ci", "npm test",
				ReportFormat.TAP, true, Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-01T00:00:00Z"))));
	}

	private void givenSuite(String storagePath) {
		when(this.testSuites.findVersion(any()))
			.thenReturn(Optional.of(new TestSuiteVersionView(SUITE, UUID.randomUUID(), "1.0.0", storagePath, "hash", 10,
					4, Instant.parse("2026-01-01T00:00:00Z"), "instructor", Instant.parse("2026-01-01T00:00:00Z"))));
	}

}
