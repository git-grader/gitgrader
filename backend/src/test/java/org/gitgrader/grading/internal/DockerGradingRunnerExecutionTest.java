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

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.KillContainerCmd;
import com.github.dockerjava.api.command.LogContainerCmd;
import com.github.dockerjava.api.command.RemoveContainerCmd;
import com.github.dockerjava.api.command.StartContainerCmd;
import com.github.dockerjava.api.command.WaitContainerCmd;
import com.github.dockerjava.api.model.Frame;
import com.github.dockerjava.api.model.StreamType;
import com.github.dockerjava.api.model.WaitResponse;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.gitgrader.grading.GradingResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers what the runner does with a container once it is running.
 *
 * <p>
 * The sibling configuration test proves the sandbox is described correctly. This one
 * drives the execute path against a Docker client that behaves the way a real one does in
 * the respect that matters most here: the log stream is a separate connection, so its
 * frames arrive after the container has already exited.
 */
class DockerGradingRunnerExecutionTest {

	private static final String CONTAINER_ID = "container-1";

	private static final String IMAGE = "image@sha256:1234";

	private DockerClient dockerClient;

	private DockerGradingRunner runner;

	private GradingExecutionRequest request;

	private KillContainerCmd killCmd;

	/** Released once the test has handed every log frame to the runner's callback. */
	private CountDownLatch logsDelivered;

	private GradingProperties properties;

	private StorageProperties storage;

	@BeforeEach
	void setUp() {
		this.dockerClient = mock(DockerClient.class);
		this.logsDelivered = new CountDownLatch(1);

		this.properties = new GradingProperties("docker", 2, Duration.ofSeconds(120), DataSize.ofMegabytes(512), 1.0,
				256, false, DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.Queue(Duration.ofSeconds(2), Duration.ofMinutes(15), 3, Duration.ofSeconds(30), 3,
						500, 1000, Duration.ofSeconds(30)));
		this.storage = new StorageProperties("/data/git/repositories", "/data/templates", "/data/tests",
				"/data/artifacts", "/data/tmp");

		this.runner = new DockerGradingRunner(this.dockerClient, this.properties, Clock.systemUTC(), this.storage,
				(image) -> Optional.empty());
		this.request = new GradingExecutionRequest(Path.of("/data/workspace/student1"), Path.of("/data/tests/suite1"),
				IMAGE, null, "npm test", Duration.ofSeconds(30), 1024L * 1024 * 256, 1.5, 128, false, 1024 * 1024,
				"corr-1", Map.of());

		stubContainerLifecycle();
	}

	@Test
	@DisplayName("captures output that only arrives after the container has exited")
	void capturesLogsDeliveredAfterTheContainerExits() throws Exception {
		streamLogsAsynchronously("1..2\nok 1 first\nok 2 second\n", "");
		completeWaitWith(0);

		GradingResult result = this.runner.execute(this.request);

		assertThat(this.logsDelivered.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(result.stdout()).isEqualTo("1..2\nok 1 first\nok 2 second\n");
		assertThat(result.exitCode()).isZero();
		assertThat(result.infrastructureFailure()).isFalse();
		assertThat(result.timedOut()).isFalse();
	}

	@Test
	@DisplayName("refuses to run a submission at all when the mounts cannot be proved")
	void refusesToRunWhenTheMountsAreUnusable() {
		// The probe answers before any container of the student's is created, so a broken
		// mount root costs an infrastructure error rather than a score of zero against an
		// empty workspace. It cannot be checked from inside the grading container: the
		// submission controls every channel that container reports through.
		DockerGradingRunner refusing = new DockerGradingRunner(this.dockerClient, this.properties, Clock.systemUTC(),
				this.storage, (image) -> Optional.of("the daemon cannot see the workspace"));

		GradingResult result = refusing.execute(this.request);

		assertThat(result.infrastructureFailure()).isTrue();
		assertThat(result.failureDetail()).isEqualTo("the daemon cannot see the workspace");
	}

	@Test
	@DisplayName("keeps standard error separate from standard output")
	void separatesStandardErrorFromStandardOutput() {
		streamLogsAsynchronously("ok 1 first\n", "warning: deprecated\n");
		completeWaitWith(0);

		GradingResult result = this.runner.execute(this.request);

		assertThat(result.stdout()).isEqualTo("ok 1 first\n");
		assertThat(result.stderr()).isEqualTo("warning: deprecated\n");
	}

	@Test
	@DisplayName("reports an infrastructure failure rather than scoring a report that never finished arriving")
	void reportsInfrastructureFailureWhenTheLogStreamNeverFinishes() {
		// The stream delivers a prefix and then stalls, exactly as a half-open
		// connection would. Scoring this would silently mark the missing tests absent.
		stubLogStream((callback) -> {
			callback.onNext(frame(StreamType.STDOUT, "1..2\nok 1 first\n"));
			this.logsDelivered.countDown();
		});
		completeWaitWith(0);

		GradingResult result = this.runner.execute(this.request);

		assertThat(result.infrastructureFailure()).isTrue();
		assertThat(result.failureDetail()).contains("incomplete");
	}

	@Test
	@DisplayName("kills the container and reports a timeout when the sandbox outlives its limit")
	void killsAndReportsTimeoutWhenTheSandboxOutlivesItsLimit() {
		streamLogsAsynchronously("1..1\n", "");
		WaitContainerCmd waitCmd = mock(WaitContainerCmd.class);
		when(waitCmd.exec(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		when(this.dockerClient.waitContainerCmd(CONTAINER_ID)).thenReturn(waitCmd);

		GradingResult result = this.runner.execute(new GradingExecutionRequest(this.request.workspaceDirectory(),
				this.request.hiddenTestsDirectory(), IMAGE, null, "npm test", Duration.ofMillis(50), 1024L * 1024 * 256,
				1.5, 128, false, 1024 * 1024, "corr-1", Map.of()));

		assertThat(result.timedOut()).isTrue();
		assertThat(result.infrastructureFailure()).isFalse();
		verify(this.killCmd).exec();
	}

	@Test
	@DisplayName("removes the container even when the sandbox could not be started")
	void removesTheContainerEvenWhenTheSandboxCouldNotBeStarted() {
		StartContainerCmd startCmd = mock(StartContainerCmd.class);
		when(startCmd.exec()).thenThrow(new IllegalStateException("engine refused"));
		when(this.dockerClient.startContainerCmd(CONTAINER_ID)).thenReturn(startCmd);

		GradingResult result = this.runner.execute(this.request);

		assertThat(result.infrastructureFailure()).isTrue();
		verify(this.dockerClient).removeContainerCmd(CONTAINER_ID);
	}

	@Test
	@DisplayName("decodes a character whose UTF-8 bytes arrive in two different frames")
	void decodesACharacterSplitAcrossTwoFrames() throws Exception {
		// Where the engine chunks the stream has nothing to do with character
		// boundaries, so decoding each frame on its own turned both halves of this
		// e-acute into replacement characters.
		byte[] line = "ok 1 - café\n".getBytes(StandardCharsets.UTF_8);
		int insideTheAccentedCharacter = "ok 1 - caf".length() + 1;

		stubLogStream((callback) -> {
			callback.onNext(new Frame(StreamType.STDOUT, Arrays.copyOfRange(line, 0, insideTheAccentedCharacter)));
			callback.onNext(
					new Frame(StreamType.STDOUT, Arrays.copyOfRange(line, insideTheAccentedCharacter, line.length)));
			callback.onComplete();
			this.logsDelivered.countDown();
		});
		completeWaitWith(0);

		GradingResult result = this.runner.execute(this.request);

		assertThat(this.logsDelivered.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(result.stdout()).isEqualTo("ok 1 - café\n");
	}

	@Test
	@DisplayName("closes the Docker log stream handle")
	void closesTheReturnedLogStream() throws Exception {
		@SuppressWarnings("unchecked")
		ResultCallback<Frame> stream = mock(ResultCallback.class);
		LogContainerCmd logCmd = mock(LogContainerCmd.class);
		when(logCmd.withStdOut(anyBoolean())).thenReturn(logCmd);
		when(logCmd.withStdErr(anyBoolean())).thenReturn(logCmd);
		when(logCmd.withFollowStream(anyBoolean())).thenReturn(logCmd);
		when(logCmd.exec(any())).thenAnswer((invocation) -> {
			ResultCallback<Frame> callback = invocation.getArgument(0);
			callback.onComplete();
			return stream;
		});
		when(this.dockerClient.logContainerCmd(CONTAINER_ID)).thenReturn(logCmd);
		completeWaitWith(0);

		this.runner.execute(this.request);

		verify(stream).close();
	}

	private void stubContainerLifecycle() {
		CreateContainerCmd createCmd = mock(CreateContainerCmd.class);
		when(this.dockerClient.createContainerCmd(IMAGE)).thenReturn(createCmd);
		when(createCmd.withHostConfig(any())).thenReturn(createCmd);
		when(createCmd.withUser(anyString())).thenReturn(createCmd);
		when(createCmd.withWorkingDir(anyString())).thenReturn(createCmd);
		when(createCmd.withEnv(anyList())).thenReturn(createCmd);
		when(createCmd.withCmd(anyList())).thenReturn(createCmd);

		CreateContainerResponse created = mock(CreateContainerResponse.class);
		when(created.getId()).thenReturn(CONTAINER_ID);
		when(createCmd.exec()).thenReturn(created);

		StartContainerCmd startCmd = mock(StartContainerCmd.class);
		when(this.dockerClient.startContainerCmd(CONTAINER_ID)).thenReturn(startCmd);

		this.killCmd = mock(KillContainerCmd.class);
		when(this.dockerClient.killContainerCmd(CONTAINER_ID)).thenReturn(this.killCmd);

		RemoveContainerCmd removeCmd = mock(RemoveContainerCmd.class);
		when(removeCmd.withForce(anyBoolean())).thenReturn(removeCmd);
		when(this.dockerClient.removeContainerCmd(CONTAINER_ID)).thenReturn(removeCmd);
	}

	/**
	 * Delivers the frames on another thread once the container has already been waited
	 * for, which is the ordering the real client produces.
	 * @param stdout what the sandbox wrote to standard output
	 * @param stderr what the sandbox wrote to standard error
	 */
	private void streamLogsAsynchronously(String stdout, String stderr) {
		stubLogStream((callback) -> {
			Thread deliver = new Thread(() -> {
				sleepBriefly();
				if (!stdout.isEmpty()) {
					callback.onNext(frame(StreamType.STDOUT, stdout));
				}
				if (!stderr.isEmpty()) {
					callback.onNext(frame(StreamType.STDERR, stderr));
				}
				callback.onComplete();
				this.logsDelivered.countDown();
			}, "log-stream");
			deliver.setDaemon(true);
			deliver.start();
		});
	}

	private void stubLogStream(java.util.function.Consumer<ResultCallback<Frame>> delivery) {
		LogContainerCmd logCmd = mock(LogContainerCmd.class);
		when(logCmd.withStdOut(anyBoolean())).thenReturn(logCmd);
		when(logCmd.withStdErr(anyBoolean())).thenReturn(logCmd);
		when(logCmd.withFollowStream(anyBoolean())).thenReturn(logCmd);
		when(logCmd.exec(any())).thenAnswer((invocation) -> {
			ResultCallback<Frame> callback = invocation.getArgument(0);
			delivery.accept(callback);
			return callback;
		});
		when(this.dockerClient.logContainerCmd(CONTAINER_ID)).thenReturn(logCmd);
	}

	private void completeWaitWith(int statusCode) {
		WaitResponse response = mock(WaitResponse.class);
		when(response.getStatusCode()).thenReturn(statusCode);

		WaitContainerCmd waitCmd = mock(WaitContainerCmd.class);
		when(waitCmd.exec(any())).thenAnswer((invocation) -> {
			ResultCallback<WaitResponse> callback = invocation.getArgument(0);
			callback.onNext(response);
			callback.onComplete();
			return callback;
		});
		when(this.dockerClient.waitContainerCmd(CONTAINER_ID)).thenReturn(waitCmd);
	}

	private static Frame frame(StreamType type, String text) {
		return new Frame(type, text.getBytes(StandardCharsets.UTF_8));
	}

	private static void sleepBriefly() {
		try {
			Thread.sleep(150);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
		}
	}

}
