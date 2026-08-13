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
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
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
 * Executes untrusted student code using Docker.
 */
@Component
@ConditionalOnProperty(name = "grading.runner", havingValue = "docker", matchIfMissing = true)
class DockerGradingRunner implements GradingRunner {

	/** Linux CFS scheduling period in microseconds; one full period equals one CPU. */
	private static final long CPU_PERIOD_MICROS = 100_000L;

	/**
	 * Floor on the quota so a very small cpu-limit cannot round down to no CPU at all.
	 */
	private static final long MINIMUM_CPU_QUOTA_MICROS = 1_000L;

	/** Bounded so a stuck log stream delays one run rather than hanging the worker. */
	private static final Duration LOG_DRAIN_TIMEOUT = Duration.ofSeconds(10);

	private static final Logger logger = LoggerFactory.getLogger(DockerGradingRunner.class);

	private final DockerClient dockerClient;

	private final GradingProperties properties;

	private final Clock clock;

	private final StorageProperties storage;

	DockerGradingRunner(DockerClient dockerClient, GradingProperties properties, Clock clock,
			StorageProperties storage) {
		this.dockerClient = dockerClient;
		this.properties = properties;
		this.clock = clock;
		this.storage = storage;
	}

	@Override
	public GradingResult execute(GradingExecutionRequest request) {
		long start = this.clock.millis();
		try {
			CreateContainerCmd cmd = createContainerCmd(request);
			CreateContainerResponse container = cmd.exec();
			String containerId = container.getId();
			try (LogCaptureCallback callback = new LogCaptureCallback(request.logSizeLimitBytes());
					WaitContainerResultCallback waitCallback = new WaitContainerResultCallback()) {

				this.dockerClient.startContainerCmd(containerId).exec();

				this.dockerClient.logContainerCmd(containerId)
					.withStdOut(true)
					.withStdErr(true)
					.withFollowStream(true)
					.exec(callback);

				this.dockerClient.waitContainerCmd(containerId).exec(waitCallback);
				boolean completed = waitCallback.awaitCompletion(request.timeout().toMillis(), TimeUnit.MILLISECONDS);

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
				// moment they exit and a following inspect loses that race and answers
				// 404. That surfaced as an infrastructure failure, which would tell a
				// student their submission broke the grader when it had in fact been
				// graded. The wait already carries the code, and needs nothing to exist.
				Integer exitCode = waitCallback.awaitStatusCode();
				return new GradingResult((exitCode != null) ? exitCode : -1, callback.getStdout(), callback.getStderr(),
						this.clock.millis() - start, false, false, null);

			}
			finally {
				try {
					this.dockerClient.removeContainerCmd(containerId).withForce(true).exec();
				}
				catch (Exception e) {
					logger.warn("Failed to remove container {}", containerId, e);
				}
			}
		}
		catch (Exception e) {
			logger.error("Infrastructure error during grading execution", e);
			return new GradingResult(-1, "", "", this.clock.millis() - start, false, true, e.getMessage());
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
	 * Creates the container configuration. Package-private for testing.
	 * @param request the grading execution request
	 * @return the configured create container command
	 */
	CreateContainerCmd createContainerCmd(GradingExecutionRequest request) {
		HostConfig hostConfig = HostConfig.newHostConfig()
			.withAutoRemove(true)
			.withReadonlyRootfs(this.properties.docker().readOnlyRootFilesystem())
			.withTmpFs(Map.of("/tmp", "size=" + this.properties.docker().tmpfsSize().toBytes()))
			.withMemory(request.memoryLimitBytes())
			.withCpuQuota(Math.max(MINIMUM_CPU_QUOTA_MICROS, (long) (request.cpuLimit() * CPU_PERIOD_MICROS)))
			.withCpuPeriod(CPU_PERIOD_MICROS)
			.withPidsLimit((long) request.pidLimit())
			.withSecurityOpts(List.of("no-new-privileges=true"));

		if (this.properties.docker().dropAllCapabilities()) {
			hostConfig.withCapDrop(Capability.ALL);
		}

		if (!request.networkEnabled()) {
			hostConfig.withNetworkMode("none");
		}

		String hostWorkspace = request.workspaceDirectory().toAbsolutePath().toString();
		if (!this.properties.docker().workspaceMountRoot().isEmpty()) {
			Path workspaceName = request.workspaceDirectory().getFileName();
			if (workspaceName == null) {
				throw new IllegalStateException(
						"Grading workspace path has no directory name: " + request.workspaceDirectory());
			}
			hostWorkspace = this.properties.docker().workspaceMountRoot() + "/" + workspaceName;
		}

		hostConfig.withBinds(new Bind(hostWorkspace, new Volume("/workspace"), AccessMode.rw),
				new Bind(hostHiddenTests(request), new Volume("/opt/hidden-tests"), AccessMode.ro));

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

	/**
	 * Resolves where the hidden tests live as the Docker daemon sees them.
	 *
	 * <p>
	 * Binds are resolved by the daemon on the host, not inside this process. When the
	 * application is itself containerised its own path for the tests means nothing there,
	 * and Docker answers a missing bind source by creating an empty directory rather than
	 * by failing. The tests then simply are not present, the runner reports no results at
	 * all, and every submission is scored zero without anything going wrong visibly.
	 * @param request the execution request
	 * @return the path to bind, translated onto the host when a root is configured
	 */
	private String hostHiddenTests(GradingExecutionRequest request) {
		Path tests = request.hiddenTestsDirectory().toAbsolutePath();
		String mountRoot = this.properties.docker().testsMountRoot();
		if (mountRoot.isEmpty()) {
			return tests.toString();
		}
		return mountRoot + "/" + this.storage.tests().relativize(tests);
	}

	/**
	 * Collects the sandbox output, up to a limit.
	 *
	 * <p>
	 * Frames arrive on a Docker client thread while the worker thread reads the result,
	 * so every access is synchronised: without it the reader is not merely racing for the
	 * last few frames, it has no guarantee of seeing any of them.
	 */
	@SuppressWarnings("PMD.AvoidStringBufferField") // bounded and per-run; never long
													// lived
	private static class LogCaptureCallback extends ResultCallback.Adapter<Frame> {

		private final long limitBytes;

		private final StringBuilder stdout = new StringBuilder();

		private final StringBuilder stderr = new StringBuilder();

		private long currentBytes;

		LogCaptureCallback(long limitBytes) {
			this.limitBytes = limitBytes;
		}

		@Override
		public synchronized void onNext(Frame frame) {
			if (currentBytes >= limitBytes) {
				return;
			}
			byte[] payload = frame.getPayload();
			long allowed = Math.min(payload.length, limitBytes - currentBytes);
			String text = new String(payload, 0, (int) allowed, StandardCharsets.UTF_8);
			if (frame.getStreamType() == StreamType.STDOUT) {
				stdout.append(text);
			}
			else if (frame.getStreamType() == StreamType.STDERR) {
				stderr.append(text);
			}
			currentBytes += allowed;
		}

		synchronized String getStdout() {
			return stdout.toString();
		}

		synchronized String getStderr() {
			return stderr.toString();
		}

	}

}
