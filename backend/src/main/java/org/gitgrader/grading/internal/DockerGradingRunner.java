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
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.exception.ConflictException;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.gitgrader.grading.GradingResult;
import org.gitgrader.grading.GradingRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Executes untrusted student code using Docker, in one container until the runtime's
 * {@code shimKind} asks for the submission and the hidden tests to be split in two.
 */
@Component
@ConditionalOnProperty(name = "grading.runner", havingValue = "docker", matchIfMissing = true)
class DockerGradingRunner implements GradingRunner {

	/** Bounded so a stuck log stream delays one run rather than hanging the worker. */
	private static final Duration LOG_DRAIN_TIMEOUT = Duration.ofSeconds(10);

	/**
	 * Why a wait on a container ended, which is not the same question as whether the
	 * container finished.
	 */
	private enum Verdict {

		/** The container ran and the wait reported its exit. */
		FINISHED,

		/** The container used the whole budget it was given. */
		TIMED_OUT,

		/** The wait ended on its own, well inside the budget. */
		ENDED_EARLY

	}

	/**
	 * Recorded when the engine stopped reporting a sandbox that had not used its budget.
	 */
	private static final String LOST_SANDBOX_DETAIL = "The engine stopped reporting the sandbox before it ran "
			+ "for as long as it was allowed to, so this run was cut short rather than finished";

	/**
	 * Recorded when the budget ran out and neither container said anything about it. The
	 * run reached its limit with the sandbox still serving and the suite still waiting,
	 * which is what an over-running submission looks like from here.
	 */
	private static final String TIMEOUT_SILENT_DETAIL = "The submission ran for the whole time it was allowed and "
			+ "never finished, and neither container reported a reason";

	private static final Logger logger = LoggerFactory.getLogger(DockerGradingRunner.class);

	private final DockerClient dockerClient;

	private final GradingProperties properties;

	private final Clock clock;

	private final StorageProperties storage;

	private final SandboxMountProbe mountProbe;

	DockerGradingRunner(DockerClient dockerClient, GradingProperties properties, Clock clock, StorageProperties storage,
			SandboxMountProbe mountProbe) {
		this.dockerClient = dockerClient;
		this.properties = properties;
		this.clock = clock;
		this.storage = storage;
		this.mountProbe = mountProbe;
	}

	@Override
	public GradingResult execute(GradingExecutionRequest request) {
		long start = this.clock.millis();
		Optional<String> unusable = this.mountProbe.unusableReason(request.runtimeImageDigest());
		if (unusable.isPresent()) {
			return new GradingResult(-1, "", "", this.clock.millis() - start, false, true, unusable.get());
		}
		if (ShimmedGradingContainer.isShimmed(request)) {
			return executeTwoContainer(request, start);
		}
		return executeSingleContainer(request, start);
	}

	private GradingResult executeSingleContainer(GradingExecutionRequest request, long start) {
		try {
			CreateContainerResponse container = createContainerCmd(request).exec();
			String containerId = container.getId();
			try (LogCaptureCallback callback = new LogCaptureCallback(request.logSizeLimitBytes());
					WaitContainerResultCallback waitCallback = new WaitContainerResultCallback()) {

				this.dockerClient.startContainerCmd(containerId).exec();

				// Attached after the sandbox starts, never before. A followed log request
				// on a container the engine is not yet running is closed at once with
				// nothing in it, so subscribing first loses the whole report rather than
				// closing a race. Nothing is missed by subscribing late, because the log
				// belongs to the container and outlives the process that wrote it: this
				// runner does not auto-remove, so the log is still there to be read.
				ResultCallback<Frame> logStream = logStream(containerId, callback);
				try (logStream) {
					this.dockerClient.waitContainerCmd(containerId).exec(waitCallback);
					Verdict verdict = awaitVerdict(request.timeout(), waitCallback);

					if (verdict == Verdict.TIMED_OUT) {
						killContainer(containerId);
						drain(callback, containerId);
						return new GradingResult(-1, callback.getStdout(), callback.getStderr(),
								this.clock.millis() - start, true, false, null);
					}

					if (verdict == Verdict.ENDED_EARLY) {
						return new GradingResult(-1, callback.getStdout(), callback.getStderr(),
								this.clock.millis() - start, false, true, LOST_SANDBOX_DETAIL);
					}

					if (!drain(callback, containerId)) {
						return new GradingResult(-1, callback.getStdout(), callback.getStderr(),
								this.clock.millis() - start, false, true,
								"The sandbox exited but its output never finished arriving, "
										+ "so the test report would have been incomplete");
					}

					// Taken from the wait result rather than by inspecting the container,
					// so nothing here depends on the container still existing. The wait
					// already carries the code and needs nothing to be left behind.
					Integer exitCode = waitCallback.awaitStatusCode();
					return new GradingResult((exitCode != null) ? exitCode : -1, callback.getStdout(),
							callback.getStderr(), this.clock.millis() - start, false, false, null);
				}

			}
			finally {
				removeContainer(containerId);
			}
		}
		catch (InterruptedException ex) {
			// Raised when the worker is interrupted mid-run, which is how an orderly
			// shutdown asks it to stop. Swallowing it left the flag clear and the thread
			// carrying on as though nothing had been asked of it, so the drain waited out
			// its whole timeout before giving up on a worker that had already been told.
			Thread.currentThread().interrupt();
			logger.warn("Grading run interrupted; the job returns to the queue");
			return interrupted(start);
		}
		catch (IOException | RuntimeException ex) {
			logger.error("Infrastructure error during grading execution", ex);
			return new GradingResult(-1, "", "", this.clock.millis() - start, false, true, ex.getMessage());
		}
	}

	/**
	 * Runs a shimmed grading round over the grading runtime protocol: the sandbox
	 * (submission, no tests mounted) and the suite (hidden tests, no workspace) in two
	 * separate containers, talking over a Unix socket, with only the suite's output read
	 * as the report.
	 *
	 * <p>
	 * The two are created, started and torn down together on every path. The sandbox is
	 * first, so that when the suite starts its shim client's connect-retry has a readily
	 * listening server rather than a sockets-in-yet race.
	 * @param request the graded shim request
	 * @param start clock reading when the run was accepted
	 * @return the result of the run, from the suite's report
	 */
	GradingResult executeTwoContainer(GradingExecutionRequest request, long start) {
		// Graded over a Unix socket they share, not a filesystem they must not share.
		ShimmedGradingContainer shim = new ShimmedGradingContainer(this.dockerClient, this.properties.docker(),
				this.storage);
		Path socketDir = null;
		String sandboxId = null;
		String suiteId = null;
		try {
			socketDir = shim.createSocketDirectory();
			sandboxId = shim.createSandbox(request, socketDir).exec().getId();
			suiteId = shim.createSuite(request, socketDir).exec().getId();
			return awaitReport(request, start, sandboxId, suiteId);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			logger.warn("Grading run interrupted; the job returns to the queue");
			return interrupted(start);
		}
		catch (IOException | RuntimeException ex) {
			logger.error("Infrastructure error during grading execution", ex);
			return new GradingResult(-1, "", "", this.clock.millis() - start, false, true, ex.getMessage());
		}
		finally {
			removeContainer(sandboxId);
			removeContainer(suiteId);
			shim.deleteSocketDirectory(socketDir);
		}
	}

	/**
	 * Drives a running sandbox-suite pair to completion.
	 * @param request the graded shim request
	 * @param start clock reading when the run was accepted
	 * @param sandboxId the created sandbox container
	 * @param suiteId the created suite container
	 * @return the result, reported from the suite's output
	 * @throws InterruptedException when the worker is interrupted mid-run
	 * @throws IOException when a resource cannot be closed
	 */
	private GradingResult awaitReport(GradingExecutionRequest request, long start, String sandboxId, String suiteId)
			throws InterruptedException, IOException {
		try (LogCaptureCallback sandboxLogs = new LogCaptureCallback(request.logSizeLimitBytes());
				LogCaptureCallback suiteLogs = new LogCaptureCallback(request.logSizeLimitBytes());
				WaitContainerResultCallback suiteWait = new WaitContainerResultCallback()) {

			this.dockerClient.startContainerCmd(sandboxId).exec();
			this.dockerClient.startContainerCmd(suiteId).exec();

			// Attached after both containers start, for the same reason as the
			// single-container path: a followed log request on a container that is not
			// running yet comes back empty and closed.
			ResultCallback<Frame> sandboxStream = logStream(sandboxId, sandboxLogs);
			ResultCallback<Frame> suiteStream = logStream(suiteId, suiteLogs);
			try (sandboxStream; suiteStream) {
				this.dockerClient.waitContainerCmd(suiteId).exec(suiteWait);
				Verdict verdict = awaitVerdict(request.timeout(), suiteWait);

				if (verdict == Verdict.TIMED_OUT) {
					// The bound is the suite's own run; once both are stopped the report
					// arrives in full, which is all a timeout is allowed to keep from the
					// student.
					killContainer(suiteId);
					killContainer(sandboxId);
					drain(suiteLogs, suiteId);
					drain(sandboxLogs, sandboxId);
					return new GradingResult(-1, suiteLogs.getStdout(), suiteLogs.getStderr(),
							this.clock.millis() - start, true, false, timeoutDetail(suiteLogs, sandboxLogs));
				}

				if (verdict == Verdict.ENDED_EARLY) {
					drain(sandboxLogs, sandboxId);
					return new GradingResult(-1, suiteLogs.getStdout(), suiteLogs.getStderr(),
							this.clock.millis() - start, false, true, lostSandboxDetail(suiteLogs, sandboxLogs));
				}

				if (!drain(suiteLogs, suiteId)) {
					return new GradingResult(-1, suiteLogs.getStdout(), suiteLogs.getStderr(),
							this.clock.millis() - start, false, true,
							"The sandbox exited but its output never finished arriving, "
									+ "so the test report would have been incomplete");
				}

				// The sandbox's chatter is diagnostic only; it can never be the annotated
				// report, so it is captured for the log and dropped.
				Integer exitCode = suiteWait.awaitStatusCode();
				return new GradingResult((exitCode != null) ? exitCode : -1, suiteLogs.getStdout(),
						suiteLogs.getStderr(), this.clock.millis() - start, false, false, null);
			}
		}
	}

	/**
	 * Connects a container's log stream to a bounded collector.
	 * @param containerId the container to follow
	 * @param callback the collector the frames feed
	 * @return the stream handle, closed when the run is done with it
	 */
	private ResultCallback<Frame> logStream(String containerId, LogCaptureCallback callback) {
		return this.dockerClient.logContainerCmd(containerId)
			.withStdOut(true)
			.withStdErr(true)
			.withFollowStream(true)
			.exec(callback);
	}

	/**
	 * Explains a timeout using whatever the two containers managed to say.
	 *
	 * <p>
	 * A run that spent its whole budget has usually told us why and the runner was too
	 * slow to read it. The shim reports an unresolvable submission (several modules and
	 * no {@code SOLUTION_PATH}) and an over-running call on stderr and then keeps
	 * running, so both messages are sitting in the captured output of a container that
	 * was then killed. Reporting them turns a run that told us nothing into one that
	 * names the file to fix, and it costs only text the runner already holds.
	 * @param suiteLogs what the suite reported before it was stopped
	 * @param sandboxLogs what the sandbox reported before it was stopped
	 * @return the detail to record, or null when neither side explained itself
	 */
	private static String timeoutDetail(LogCaptureCallback suiteLogs, LogCaptureCallback sandboxLogs) {
		String detail = firstNonBlank(sandboxLogs.getStderr(), suiteLogs.getStderr());
		if (detail == null) {
			// Nothing was said, and the distinction still matters to whoever reads the
			// row: the budget ran out with the sandbox still serving, or it had already
			// gone. Only the first is a slow submission.
			return sandboxLogs.getStdout().isBlank() ? TIMEOUT_SILENT_DETAIL : null;
		}
		return detail;
	}

	/**
	 * Explains a wait that ended while the sandbox was still supposed to be running.
	 * @param suiteLogs what the suite reported
	 * @param sandboxLogs what the sandbox reported
	 * @return the detail to record, always naming the lost sandbox
	 */
	private static String lostSandboxDetail(LogCaptureCallback suiteLogs, LogCaptureCallback sandboxLogs) {
		String said = firstNonBlank(sandboxLogs.getStderr(), suiteLogs.getStderr());
		return (said == null) ? LOST_SANDBOX_DETAIL : LOST_SANDBOX_DETAIL + ": " + said;
	}

	/**
	 * Returns the first of the given texts that carries anything.
	 * @param candidates the texts to consider, in order of preference
	 * @return the first non-blank candidate, or null when all are blank
	 */
	private static String firstNonBlank(String... candidates) {
		for (String candidate : candidates) {
			if (candidate != null && !candidate.isBlank()) {
				return candidate.strip();
			}
		}
		return null;
	}

	private GradingResult interrupted(long start) {
		return new GradingResult(-1, "", "", this.clock.millis() - start, false, true,
				"The grading worker was interrupted before the sandbox finished");
	}

	/**
	 * Waits for a container and works out why the wait ended.
	 *
	 * <p>
	 * {@code awaitCompletion} answers false for two unrelated things: a budget that ran
	 * out, and a callback the engine tore down early. A container reaped out from under
	 * an open wait is the second, and reading it as the first made a run that finished in
	 * a second look like one that ran to its limit - so it was killed (on a container
	 * that no longer existed, which threw), reported as an infrastructure failure, and
	 * its report thrown away. Only a wait that genuinely spent its whole budget is a
	 * timeout; anything else that did not finish is reported for what it is.
	 * @param budget how long the container was allowed to run
	 * @param waitCallback the wait being awaited
	 * @return why the wait ended
	 * @throws InterruptedException when the worker is interrupted mid-wait
	 */
	private Verdict awaitVerdict(Duration budget, WaitContainerResultCallback waitCallback)
			throws InterruptedException {
		long startedAt = this.clock.millis();
		if (waitCallback.awaitCompletion(budget.toMillis(), TimeUnit.MILLISECONDS)) {
			return Verdict.FINISHED;
		}
		return (this.clock.millis() - startedAt >= budget.toMillis()) ? Verdict.TIMED_OUT : Verdict.ENDED_EARLY;
	}

	/**
	 * Kills a container, treating one that has already gone as the outcome wanted.
	 *
	 * <p>
	 * A sandbox that exited a moment before the kill landed is stopped, which is all the
	 * kill was for. Docker says so as 404 (already reaped) and 409 (being reaped), and
	 * letting either escape turned a timeout into an infrastructure failure carrying no
	 * output at all - a student told the platform had broken over code that merely took
	 * too long, with the partial report that proved it thrown away.
	 * @param containerId the container to kill
	 */
	private void killContainer(String containerId) {
		try {
			this.dockerClient.killContainerCmd(containerId).exec();
		}
		catch (NotFoundException | ConflictException alreadyGone) {
			logger.debug("Container {} had already stopped when the sandbox was killed", containerId);
		}
	}

	/**
	 * Removes a container, tolerating the races the engine creates.
	 * @param containerId the container to remove, or {@code null} when none was created
	 */
	private void removeContainer(String containerId) {
		if (containerId == null) {
			return;
		}
		try {
			this.dockerClient.removeContainerCmd(containerId).withForce(true).exec();
		}
		catch (NotFoundException | ConflictException expected) {
			// Grading containers are removed explicitly rather than by auto-remove, so
			// this is now the rare case rather than the common one: the engine had
			// already taken this one (404), or still had it in hand (409). Both mean
			// the container is gone or going, which is what was wanted. Logged as a
			// warning with a stack trace, this used to print on every successful run
			// and taught an operator to ignore the warnings from this class.
			logger.debug("Container {} was already gone or going by the time it was removed", containerId);
		}
		catch (RuntimeException ex) {
			logger.warn("Failed to remove container {}", containerId, ex);
		}
	}

	/**
	 * Waits for the log stream to finish after the sandbox has stopped.
	 *
	 * <p>
	 * Waiting on the container and reading its output are two different connections, and
	 * the wait returns the moment the process exits, not the moment its last frames have
	 * been delivered. Reading the buffers straight away therefore truncates the report -
	 * which does not look like a failure, because a short TAP report is a valid TAP
	 * report. It simply contains fewer tests than the suite ran, and the student is
	 * scored on whatever happened to arrive in time.
	 * @param callback the capture being filled by the log stream
	 * @param containerId the container being drained, for logging
	 * @return whether the stream finished within {@link #LOG_DRAIN_TIMEOUT}
	 */
	private boolean drain(LogCaptureCallback callback, String containerId) {
		try {
			if (callback.awaitCompletion(LOG_DRAIN_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
				return true;
			}
			logger.warn("Log stream for container {} did not finish within {}", containerId, LOG_DRAIN_TIMEOUT);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			logger.warn("Interrupted while reading the log stream for container {}", containerId);
		}
		catch (RuntimeException ex) {
			logger.warn("Log stream for container {} ended in error", containerId, ex);
		}
		return false;
	}

	/**
	 * Creates the legacy single-container configuration. Package-private for testing.
	 * @param request the grading execution request
	 * @return the configured create container command
	 */
	CreateContainerCmd createContainerCmd(GradingExecutionRequest request) {
		HostConfig hostConfig = SandboxConfig.baseHostConfig(this.properties.docker(), request);
		hostConfig.withBinds(
				new Bind(SandboxConfig.hostWorkspace(this.properties.docker(), request), new Volume("/workspace"),
						AccessMode.rw),
				new Bind(SandboxConfig.hostHiddenTests(this.properties.docker(), this.storage, request),
						new Volume("/opt/hidden-tests"), AccessMode.ro));

		List<String> env = new ArrayList<>();
		request.environment().forEach((k, v) -> env.add(k + "=" + v));

		List<String> cmdArgs = new ArrayList<>();
		cmdArgs.add("sh");
		cmdArgs.add("-c");
		String commandStr = request.testCommand();
		String installCommand = request.installCommand();
		if (installCommand != null && !installCommand.isBlank()) {
			commandStr = installCommand + " && " + commandStr;
		}
		cmdArgs.add(commandStr);

		return this.dockerClient.createContainerCmd(request.runtimeImageDigest())
			.withHostConfig(hostConfig)
			.withUser(this.properties.docker().user())
			.withWorkingDir("/workspace")
			.withEnv(env)
			.withCmd(cmdArgs);
	}

}
