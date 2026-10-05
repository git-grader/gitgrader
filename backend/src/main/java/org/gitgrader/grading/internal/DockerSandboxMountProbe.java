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
 *
 * <p>
 * Both roots are proved, not just the workspace. The hidden tests root fails differently
 * and worse: Docker answers a missing bind source by creating an empty directory rather
 * than by failing, so a tests root that resolves to nothing leaves the sandbox running
 * with no tests present. Nothing then reports a result, and unless that is noticed every
 * submission is scored zero without any error anywhere. Proving only the workspace could
 * never catch it.
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

	/** The answers cannot change while the process runs, so each is asked once. */
	private final AtomicBoolean workspaceProved = new AtomicBoolean();

	private final AtomicBoolean testsProved = new AtomicBoolean();

	DockerSandboxMountProbe(DockerClient dockerClient, GradingProperties properties, StorageProperties storage) {
		this.dockerClient = dockerClient;
		this.properties = properties;
		this.storage = storage;
	}

	@Override
	public Optional<String> unusableReason(String image) {
		Optional<String> workspace = proveWorkspaceRoot(image);
		if (workspace.isPresent()) {
			return workspace;
		}
		return proveTestsRoot(image);
	}

	private Optional<String> proveWorkspaceRoot(String image) {
		if (this.workspaceProved.get()) {
			return Optional.empty();
		}
		String marker = "gitgrader-mount-probe-" + UUID.randomUUID();
		Path probe = this.storage.temp().resolve(marker);
		try {
			Files.createDirectories(probe);
			Files.writeString(probe.resolve("probe"), marker, StandardCharsets.UTF_8);
			String hostPath = configuredOrAbsolute(this.properties.docker().workspaceMountRoot(), this.storage.temp(),
					probe);
			if (!daemonSees(image, hostPath)) {
				return Optional.of("The Docker daemon does not resolve " + hostPath
						+ " to the directory this application writes submissions into, so a sandbox would "
						+ "start against an empty workspace. Set grading.docker.workspace-mount-root and "
						+ "grading.docker.tests-mount-root to paths that daemon can resolve itself.");
			}
			this.workspaceProved.set(true);
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

	private Optional<String> proveTestsRoot(String image) {
		if (this.testsProved.get()) {
			return Optional.empty();
		}
		String marker = "gitgrader-mount-probe-" + UUID.randomUUID();
		Path probe = this.storage.tests().resolve(marker);
		try {
			Files.createDirectories(probe);
			Files.writeString(probe.resolve("probe"), marker, StandardCharsets.UTF_8);
			// Built the same way SandboxConfig.hostHiddenTests builds a real bind, so the
			// probe
			// fails whenever grading would, rather than whenever some other path
			// convention
			// happens to disagree.
			String hostPath = configuredOrAbsolute(this.properties.docker().testsMountRoot(), this.storage.tests(),
					probe);
			if (!daemonSees(image, hostPath)) {
				return Optional.of("The Docker daemon does not resolve " + hostPath
						+ " to the directory this application stores hidden tests in. Docker creates an "
						+ "empty directory for a bind source it cannot resolve instead of failing, so the "
						+ "sandbox would run without any tests and every submission would be scored zero "
						+ "with no visible error. Set grading.docker.tests-mount-root to a path the daemon "
						+ "can resolve itself, to the same location the application mounts as its tests "
						+ "directory.");
			}
			this.testsProved.set(true);
			return Optional.empty();
		}
		catch (IOException ex) {
			logger.warn("Could not write the grading tests mount probe", ex);
			return Optional.of("Could not write to the grading tests directory: " + ex.getMessage());
		}
		finally {
			deleteQuietly(probe);
		}
	}

	/**
	 * Translates a path in this application's filesystem onto the host the daemon binds.
	 * An unset root means the daemon resolves the application's own absolute path, which
	 * is what {@link SandboxConfig} does and therefore what is proved here.
	 * @param configuredRoot the configured mount root, empty when the daemon binds
	 * directly
	 * @param base the storage root the probe was written under, which is what the
	 * configured root is interpreted as
	 * @param path the probe path inside {@code base}
	 * @return the path the daemon will be asked to bind
	 */
	private String configuredOrAbsolute(String configuredRoot, Path base, Path path) {
		if (configuredRoot.isEmpty()) {
			return path.toAbsolutePath().toString();
		}
		return configuredRoot + "/" + base.relativize(path);
	}

	private boolean daemonSees(String image, String hostPath) {
		CreateContainerResponse container = this.dockerClient.createContainerCmd(image)
			.withHostConfig(HostConfig.newHostConfig()
				.withAutoRemove(true)
				.withNetworkMode("none")
				.withBinds(new Bind(hostPath, new Volume("/probe"), AccessMode.ro)))
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
