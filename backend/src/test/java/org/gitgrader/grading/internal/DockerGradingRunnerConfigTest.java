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
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import org.gitgrader.grading.GradingExecutionRequest;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyList;

class DockerGradingRunnerConfigTest {

	private DockerClient dockerClient;

	private GradingProperties properties;

	private Clock clock;

	private GradingExecutionRequest request;

	private CreateContainerCmd cmd;

	@BeforeEach
	void setUp() {
		this.dockerClient = mock(DockerClient.class);
		this.properties = new GradingProperties("docker", "/data/grading", 2, Duration.ofSeconds(120),
				DataSize.ofMegabytes(512), 1.0, 256, false, DataSize.ofMegabytes(1), Duration.ofSeconds(20), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.Queue(Duration.ofSeconds(2), Duration.ofMinutes(15), 3, Duration.ofSeconds(30), 3,
						500, 1000, Duration.ofSeconds(30)));
		this.clock = Clock.systemUTC();

		this.request = new GradingExecutionRequest(Path.of("/data/workspace/student1"), Path.of("/data/tests/suite1"),
				"image@sha256:1234", null, "npm test", Duration.ofSeconds(30), 1024L * 1024 * 256, 1.5, 128, false,
				1024 * 1024, "corr-1", Map.of("FOO", "BAR"));

		this.cmd = mock(CreateContainerCmd.class);
		when(this.dockerClient.createContainerCmd("image@sha256:1234")).thenReturn(this.cmd);
		when(this.cmd.withHostConfig(any())).thenReturn(this.cmd);
		when(this.cmd.withUser(anyString())).thenReturn(this.cmd);
		when(this.cmd.withWorkingDir(anyString())).thenReturn(this.cmd);
		when(this.cmd.withEnv(anyList())).thenReturn(this.cmd);
		when(this.cmd.withCmd(anyList())).thenReturn(this.cmd);
	}

	@Test
	void verifySecurityConfig() {
		DockerGradingRunner runner = new DockerGradingRunner(this.dockerClient, this.properties, this.clock,
				new StorageProperties("/data/git/repositories", "/data/templates", "/data/tests", "/data/artifacts",
						"/data/tmp"));
		runner.createContainerCmd(this.request);

		ArgumentCaptor<HostConfig> hostConfigCaptor = ArgumentCaptor.forClass(HostConfig.class);
		verify(this.cmd).withHostConfig(hostConfigCaptor.capture());
		verify(this.cmd).withUser("65534:65534");
		verify(this.cmd).withWorkingDir("/workspace");

		HostConfig hostConfig = hostConfigCaptor.getValue();

		assertThat(hostConfig.getReadonlyRootfs()).isTrue();
		assertThat(hostConfig.getAutoRemove()).isTrue();
		assertThat(hostConfig.getTmpFs()).containsEntry("/tmp", "size=" + DataSize.ofMegabytes(64).toBytes());
		assertThat(hostConfig.getMemory()).isEqualTo(1024L * 1024 * 256);
		assertThat(hostConfig.getCpuQuota()).isEqualTo(150000L); // 1.5 * 100000
		assertThat(hostConfig.getPidsLimit()).isEqualTo(128L);
		assertThat(hostConfig.getSecurityOpts()).contains("no-new-privileges=true");
		assertThat(hostConfig.getCapDrop()).containsExactly(Capability.ALL);
		assertThat(hostConfig.getNetworkMode()).isEqualTo("none");

		Bind[] binds = hostConfig.getBinds();
		assertThat(binds).hasSize(2);
		assertThat(binds[0].getPath()).isEqualTo(Path.of("/data/workspace/student1").toAbsolutePath().toString());
		assertThat(binds[0].getVolume().getPath()).isEqualTo("/workspace");
		assertThat(binds[0].getAccessMode()).isEqualTo(AccessMode.rw);

		assertThat(binds[1].getPath()).isEqualTo(Path.of("/data/tests/suite1").toAbsolutePath().toString());
		assertThat(binds[1].getVolume().getPath()).isEqualTo("/opt/hidden-tests");
		assertThat(binds[1].getAccessMode()).isEqualTo(AccessMode.ro);
	}

}
