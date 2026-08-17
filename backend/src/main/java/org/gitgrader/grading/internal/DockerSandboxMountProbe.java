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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.command.WaitContainerResultCallback;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Proves the bind roots by writing a file and asking the daemon to find it.
 */
@Component
@ConditionalOnProperty(name = "grading.runner", havingValue = "docker", matchIfMissing = true)
class DockerSandboxMountProbe implements SandboxMountProbe {

	/** The probe starts a container and tests one file, nothing more. */
	private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(60);

	private static final Logger logger = LoggerFactory.getLogger(DockerSandboxMountProbe.class);

	private final DockerClient dockerClient;

	private final GradingProperties properties;

	private final StorageProperties storage;

	/** The answer cannot change while the process runs, so it is asked once. */
	private final AtomicBoolean proved = new AtomicBoolean();

	DockerSandboxMountProbe(DockerClient dockerClient, GradingProperties properties, StorageProperties storage) {
		this.dockerClient = dockerClient;
		this.properties = properties;
		this.storage = storage;
	}

	@Override
	public Optional<String> unusableReason(String image) {
		if (this.proved.get()) {
			return Optional.empty();
		}
		String marker = "gitgrader-mount-probe-" + UUID.randomUUID();
		Path probe = this.storage.temp().resolve(marker);
		try {
			Files.createDirectories(probe);
			Files.writeString(probe.resolve("probe"), marker, StandardCharsets.UTF_8);
			if (!daemonSees(image, marker)) {
				return Optional.of("The Docker daemon does not resolve " + mountRoot(marker)
						+ " to the directory this application writes submissions into, so a sandbox would "
						+ "start against an empty workspace. Set grading.docker.workspace-mount-root and "
						+ "grading.docker.tests-mount-root to paths that daemon can resolve itself.");
			}
			this.proved.set(true);
			return Optional.empty();
		}
		catch (IOException ex) {
			logger.warn("Could not write the grading mount probe", ex);
			return Optional.of("Could not write to the grading workspace directory: " + ex.getMessage());
		}
		finally {
			deleteQuietly(probe);
		}
	}

	private String mountRoot(String marker) {
		String configured = this.properties.docker().workspaceMountRoot();
		return configured.isEmpty() ? this.storage.temp().resolve(marker).toAbsolutePath().toString()
				: configured + "/" + marker;
	}

	private boolean daemonSees(String image, String marker) {
		CreateContainerResponse container = this.dockerClient.createContainerCmd(image)
			.withHostConfig(HostConfig.newHostConfig()
				.withAutoRemove(true)
				.withNetworkMode("none")
				.withBinds(new Bind(mountRoot(marker), new Volume("/probe"), AccessMode.ro)))
			.withCmd(List.of("sh", "-c", "test -f /probe/probe"))
			.exec();
		try (WaitContainerResultCallback wait = new WaitContainerResultCallback()) {
			this.dockerClient.startContainerCmd(container.getId()).exec();
			this.dockerClient.waitContainerCmd(container.getId()).exec(wait);
			Integer status = wait.awaitStatusCode(PROBE_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
			return status != null && status == 0;
		}
		catch (IOException | RuntimeException ex) {
			logger.warn("Could not prove the grading sandbox mounts", ex);
			return false;
		}
	}

	private static void deleteQuietly(Path directory) {
		try (Stream<Path> paths = Files.walk(directory)) {
			paths.sorted(Comparator.reverseOrder()).forEach((path) -> {
				try {
					Files.deleteIfExists(path);
				}
				catch (IOException ex) {
					logger.debug("Could not remove probe path {}", path, ex);
				}
			});
		}
		catch (IOException ex) {
			logger.debug("Could not remove probe directory {}", directory, ex);
		}
	}

}
