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

				ResultCallback<Frame> logStream = logStream(containerId, callback);
				try (logStream) {
					this.dockerClient.waitContainerCmd(containerId).exec(waitCallback);
					boolean completed = waitCallback.awaitCompletion(request.timeout().toMillis(),
							TimeUnit.MILLISECONDS);

					if (!completed) {
						this.dockerClient.killContainerCmd(containerId).exec();
						drain(callback, containerId);
						return new GradingResult(-1, callback.getStdout(), callback.getStderr(),
								this.clock.millis() - start, true, false, null);
					}

					if (!drain(callback, containerId)) {
						return new GradingResult(-1, callback.getStdout(), callback.getStderr(),
								this.clock.millis() - start, false, true,
								"The sandbox exited but its output never finished arriving, "
										+ "so the test report would have been incomplete");
					}

					// Taken from the wait result rather than by inspecting the container.
					// Containers are created with auto-remove, so Docker deletes them the
					// moment they exit and a following inspect loses that race and
					// answers
					// 404. That surfaced as an infrastructure failure, which would tell a
					// student their submission broke the grader when it had in fact been
					// graded. The wait already carries the code, and needs nothing to
					// exist.
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

			ResultCallback<Frame> sandboxStream = logStream(sandboxId, sandboxLogs);
			ResultCallback<Frame> suiteStream = logStream(suiteId, suiteLogs);
			try (sandboxStream; suiteStream) {
				this.dockerClient.waitContainerCmd(suiteId).exec(suiteWait);
				boolean completed = suiteWait.awaitCompletion(request.timeout().toMillis(), TimeUnit.MILLISECONDS);

				if (!completed) {
					// The bound is the suite's own run; once both are stopped the report
					// arrives in full, which is all a timeout is allowed to keep from the
					// student.
					this.dockerClient.killContainerCmd(suiteId).exec();
					this.dockerClient.killContainerCmd(sandboxId).exec();
					drain(suiteLogs, suiteId);
					return new GradingResult(-1, suiteLogs.getStdout(), suiteLogs.getStderr(),
							this.clock.millis() - start, true, false, null);
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

	private GradingResult interrupted(long start) {
		return new GradingResult(-1, "", "", this.clock.millis() - start, false, true,
				"The grading worker was interrupted before the sandbox finished");
	}

	/**
	 * Removes a container, tolerating the races auto-remove creates.
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
			// Containers are created with auto-remove, so Docker is usually already
			// removing this one by the time we ask: the answer is 404 if it finished and
			// 409 if it is still going. Both mean the container is gone or going, which
			// is
			// what was wanted. Logged as a warning with a stack trace, this printed one
			// on
			// every successful run and taught an operator to ignore the warnings from
			// this
			// class.
			logger.debug("Container {} was already being removed by Docker", containerId);
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
