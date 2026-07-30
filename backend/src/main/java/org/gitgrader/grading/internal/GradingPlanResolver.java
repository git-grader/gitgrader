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

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.runtimes.RuntimeCatalog;
import org.gitgrader.runtimes.RuntimeView;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.templates.TestSuiteCatalog;
import org.gitgrader.templates.TestSuiteVersionView;
import org.springframework.stereotype.Component;

/**
 * Gathers everything a grading run needs before the sandbox starts.
 *
 * <p>
 * Split out of {@code GradingExecutor} so that the executor deals with running and
 * interpreting, and this class deals with looking things up. It also keeps the executor's
 * collaborator list honest: five read-only catalogs behind one resolver says what the
 * design actually is, where five constructor parameters just said "this class knows about
 * everything".
 *
 * <p>
 * Every lookup fails loudly. A run whose runtime or test suite has gone missing cannot
 * produce a defensible grade, so it becomes an infrastructure error rather than a zero.
 */
@Component
public class GradingPlanResolver {

	private final SubmissionService submissions;

	private final AssignmentCatalog assignments;

	private final RuntimeCatalog runtimes;

	private final TestSuiteCatalog testSuites;

	private final StorageProperties storage;

	public GradingPlanResolver(SubmissionService submissions, AssignmentCatalog assignments, RuntimeCatalog runtimes,
			TestSuiteCatalog testSuites, StorageProperties storage) {
		this.submissions = submissions;
		this.assignments = assignments;
		this.runtimes = runtimes;
		this.testSuites = testSuites;
		this.storage = storage;
	}

	/**
	 * Resolves everything needed to grade one submission.
	 * @param submissionId the submission under grading
	 * @return the resolved plan
	 * @throws IllegalStateException when anything the run depends on is missing
	 */
	public GradingPlan resolve(UUID submissionId) {
		SubmissionView submission = this.submissions.findById(submissionId)
			.orElseThrow(() -> new IllegalStateException("Submission " + submissionId + " is gone"));

		String repositoryPath = submission.repositoryPath();
		if (repositoryPath == null) {
			throw new IllegalStateException(
					"Submission " + submissionId + " has no repository path and cannot be reproduced");
		}

		AssignmentView assignment = this.assignments.findAssignment(submission.assignmentId())
			.orElseThrow(() -> new IllegalStateException("Assignment " + submission.assignmentId() + " is gone"));
		RuntimeView runtime = Optional.ofNullable(assignment.runtimeId())
			.flatMap(this.runtimes::findRuntime)
			.orElseThrow(() -> new IllegalStateException("Assignment " + assignment.id() + " has no usable runtime"));
		TestSuiteVersionView suite = Optional.ofNullable(assignment.testSuiteVersionId())
			.flatMap(this.testSuites::findVersion)
			.orElseThrow(
					() -> new IllegalStateException("Assignment " + assignment.id() + " has no published test suite"));

		// Resolved through the guard so a stored path can never walk out of the tests
		// root.
		Path hiddenTests = StorageProperties.resolveInside(this.storage.tests(), suite.storagePath());
		return new GradingPlan(submission, repositoryPath, assignment, runtime, hiddenTests);
	}

	/**
	 * Everything one grading run needs, resolved up front.
	 *
	 * @param submission the submission under grading
	 * @param repositoryPath where its objects live
	 * @param assignment the assignment being answered
	 * @param runtime the digest-pinned runtime to execute in
	 * @param hiddenTests the hidden suite directory on the host
	 */
	public record GradingPlan(SubmissionView submission, String repositoryPath, AssignmentView assignment,
			RuntimeView runtime, Path hiddenTests) {
	}

}
