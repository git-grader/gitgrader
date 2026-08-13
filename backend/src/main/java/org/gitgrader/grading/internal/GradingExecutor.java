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

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.grading.FailureCategory;
import org.gitgrader.grading.GradingExecutionRequest;
import org.gitgrader.grading.GradingResult;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.GradingRunner;
import org.gitgrader.grading.GradingScore;
import org.gitgrader.grading.GradingScorer;
import org.gitgrader.grading.TestOutcome;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.grading.domain.TestResultRecord;
import org.gitgrader.grading.internal.GradingPlanResolver.GradingPlan;
import org.gitgrader.runtimes.RuntimeView;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * Runs one grading job: prepare, execute, interpret, score.
 *
 * <p>
 * Deals only with what a grading run <em>means</em>. Looking things up is
 * {@link GradingPlanResolver}'s job and queue mechanics are {@code GradingDispatcher}'s,
 * so this class performs no transaction management of its own: the caller decides what is
 * written and when.
 */
@Component
public class GradingExecutor {

	private static final Logger logger = LoggerFactory.getLogger(GradingExecutor.class);

	/** Where the hidden suite is mounted, deliberately outside the student workspace. */
	private static final String HIDDEN_TESTS_MOUNT = "/opt/hidden-tests";

	private final GradingRunner runner;

	private final GradingWorkspaceFactory workspaces;

	private final GradingPlanResolver plans;

	private final ReportParser reportParser;

	private final TestResultRepository testResults;

	private final GradingProperties properties;

	private final ObjectMapper objectMapper;

	private final Clock clock;

	public GradingExecutor(GradingRunner runner, GradingWorkspaceFactory workspaces, GradingPlanResolver plans,
			ReportParser reportParser, TestResultRepository testResults, GradingProperties properties,
			ObjectMapper objectMapper, Clock clock) {
		this.runner = runner;
		this.workspaces = workspaces;
		this.plans = plans;
		this.reportParser = reportParser;
		this.testResults = testResults;
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.clock = clock;
	}

	/**
	 * Prepares and executes one grading run.
	 * @param run the run to execute
	 * @return everything needed to persist the outcome
	 */
	public Outcome execute(GradingRun run) {
		GradingPlan plan = this.plans.resolve(run.submissionId());

		Path workspace;
		try {
			workspace = this.workspaces.materialise(plan.repositoryPath(), plan.submission().commitSha());
		}
		catch (IOException ex) {
			throw new IllegalStateException("Could not materialise the submitted commit", ex);
		}

		try {
			GradingResult result = this.runner
				.execute(buildRequest(run, plan.assignment(), plan.runtime(), workspace, plan.hiddenTests()));
			return interpret(run, plan.assignment(), result, workspace, plan.hiddenTests());
		}
		catch (RuntimeException ex) {
			// The workspace reaches the caller only inside a returned Outcome, so
			// anything thrown between here and the return strands a full copy of the
			// student's repository on disk. An unreadable hidden manifest does it, and
			// then does it again for every submission to that assignment.
			discardWorkspace(workspace);
			throw ex;
		}
	}

	/**
	 * Writes the outcome of a run.
	 * @param run the run being completed
	 * @param outcome what the run produced
	 */
	public void persist(GradingRun run, Outcome outcome) {
		GradingScore score = outcome.score();
		if (score == null) {
			run.fail(FailureCategory.INFRASTRUCTURE_ERROR, String.valueOf(outcome.failureDetail()),
					outcome.timedOut() ? GradingRunStatus.TIMEOUT : GradingRunStatus.INFRASTRUCTURE_ERROR, this.clock);
			return;
		}
		List<ParsedResult> results = outcome.results();
		for (int order = 0; order < results.size(); order++) {
			this.testResults.save(toRecord(run, results.get(order), order));
		}
		run.complete(score, outcome.exitCode(), outcome.durationMillis(), this.clock);
	}

	/**
	 * Removes a workspace once its run is finished.
	 * @param workspace the directory to remove
	 */
	public void discardWorkspace(Path workspace) {
		if (!this.properties.retainWorkspaces()) {
			this.workspaces.discard(workspace);
		}
	}

	private Outcome interpret(GradingRun run, AssignmentView assignment, GradingResult result, Path workspace,
			Path hiddenTests) {
		if (result.infrastructureFailure() || result.timedOut()) {
			// An unusable sandbox produces no score at all. Scoring zero here would be
			// indistinguishable from a student who passed nothing.
			return new Outcome(workspace, List.of(), null, result.exitCode(), result.durationMillis(),
					result.timedOut(), String.valueOf(result.failureDetail()));
		}

		Manifest manifest = readManifest(hiddenTests);
		List<ParsedResult> parsed = this.reportParser.parse(result.stdout(), result.stderr(), manifest);
		List<TestOutcome> outcomes = parsed.stream().map(ParsedResult::outcome).toList();
		GradingScore score = GradingScorer.score(outcomes, assignment.maxPoints(), assignment.passThreshold());

		logger.debug("Run {} produced {} test result(s) [correlationId={}]", run.id(), parsed.size(),
				run.correlationId());
		return new Outcome(workspace, parsed, score, result.exitCode(), result.durationMillis(), false, null);
	}

	/**
	 * Builds the sandbox request, letting the assignment override the global limits.
	 *
	 * <p>
	 * The per-assignment overrides exist so one heavy assignment does not force every
	 * course to run with a generous timeout. A null override means "use the deployment
	 * default", which is why each is resolved individually.
	 * @param run the run being executed
	 * @param assignment the assignment being answered
	 * @param runtime the pinned runtime
	 * @param workspace the materialised student code
	 * @param hiddenTests the hidden suite, mounted read only outside the workspace
	 * @return the request handed to the runner
	 */
	private GradingExecutionRequest buildRequest(GradingRun run, AssignmentView assignment, RuntimeView runtime,
			Path workspace, Path hiddenTests) {
		// Each override is read once into a local: calling a @Nullable accessor twice
		// (null-check, then use) is a pattern static analysis rightly flags, because
		// nothing guarantees the second call returns what the first one did.
		Integer timeoutOverride = assignment.timeoutSeconds();
		Long memoryOverride = assignment.memoryLimitBytes();
		java.math.BigDecimal cpuOverride = assignment.cpuLimit();
		Integer pidOverride = assignment.pidLimit();

		Duration timeout = (timeoutOverride != null) ? Duration.ofSeconds(timeoutOverride)
				: this.properties.defaultTimeout();
		long memory = (memoryOverride != null) ? memoryOverride : this.properties.defaultMemoryLimit().toBytes();
		double cpu = (cpuOverride != null) ? cpuOverride.doubleValue() : this.properties.defaultCpuLimit();
		int pids = (pidOverride != null) ? pidOverride : this.properties.defaultPidLimit();
		boolean network = this.properties.networkEnabled() && assignment.networkEnabled();

		return new GradingExecutionRequest(workspace, hiddenTests, runtime.pinnedReference(), runtime.installCommand(),
				runtime.testCommand(), timeout, memory, cpu, pids, network, this.properties.logSizeLimit().toBytes(),
				run.correlationId(), Map.of("HIDDEN_TESTS", HIDDEN_TESTS_MOUNT));
	}

	private Manifest readManifest(Path hiddenTests) {
		Path manifest = hiddenTests.resolve("manifest.json");
		if (!Files.isRegularFile(manifest)) {
			logger.warn("No manifest.json at {}; hidden tests will be reported without categories", hiddenTests);
			return new Manifest("unknown", "0", List.of());
		}
		try {
			return this.objectMapper.readValue(manifest.toFile(), Manifest.class);
		}
		catch (RuntimeException ex) {
			// Jackson 3 reports parse problems as unchecked JacksonException, so this
			// cannot be narrowed to IOException the way the Jackson 2 API allowed.
			throw new IllegalStateException("Could not read the hidden test manifest", ex);
		}
	}

	private static TestResultRecord toRecord(GradingRun run, ParsedResult parsed, int order) {
		boolean hidden = TestResultRecord.VISIBILITY_HIDDEN.equals(parsed.visibility());
		// For a hidden test the student-facing columns get the manifest's category and
		// hint; the real name and assertion output stay in the instructor-only columns.
		return new TestResultRecord(run.id(), parsed.visibility(), parsed.category(), parsed.testName(),
				hidden ? parsed.category() : parsed.testName(), parsed.outcome(), parsed.weight(), parsed.durationMs(),
				hidden ? parsed.hint() : parsed.studentMessage(), parsed.internalMessage(), order);
	}

	/**
	 * Everything one grading run produced.
	 *
	 * @param workspace the scratch directory, so the caller can clean it up
	 * @param results the parsed per-test outcomes
	 * @param score the computed score, or {@code null} when the run could not be scored
	 * @param exitCode the sandbox exit code
	 * @param durationMillis how long the sandbox ran
	 * @param timedOut whether the sandbox was killed for exceeding its limit
	 * @param failureDetail why no score was produced, when that happened
	 */
	public record Outcome(Path workspace, List<ParsedResult> results, @Nullable GradingScore score, int exitCode,
			long durationMillis, boolean timedOut, @Nullable String failureDetail) {
	}

}
