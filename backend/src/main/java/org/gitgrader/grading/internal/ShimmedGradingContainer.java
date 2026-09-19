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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateContainerCmd;
import com.github.dockerjava.api.model.AccessMode;
import com.github.dockerjava.api.model.Bind;
import com.github.dockerjava.api.model.HostConfig;
import com.github.dockerjava.api.model.Volume;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The two containers of a shimmed grading round, and the Unix socket they share.
 *
 * <p>
 * A shimmed round never gives the submission and the hidden tests the same filesystem:
 * the sandbox gets the workspace and no tests, the suite gets the tests and no workspace,
 * and the protocol runs over a socket in a directory both can write but neither owns. The
 * engine holds the container descriptions; this class holds the shim's own anatomy.
 */
final class ShimmedGradingContainer {

	/**
	 * Container path of the Unix socket mount shared by the two containers of a shimmed
	 * run.
	 */
	private static final String SHIM_SOCKET_VOLUME = "/gitgrader-shim";

	/**
	 * Socket inside that mount; the sandbox's server listens and the suite's client dials
	 * here.
	 */
	private static final String SHIM_SOCKET_PATH = "/gitgrader-shim/runner.sock";

	/**
	 * Where the grading runtime shim itself lives in the containers; a bind replaces this
	 * until images carry it.
	 */
	private static final String SHIM_RUNTIME_MOUNT = "/opt/gitgrader-shim";

	/** Environment variable that names the socket for the shim server and its client. */
	private static final String SHIM_SOCKET_ENV = "SHIM_SOCKET";

	/** The sandbox must not be handed the very tests it never mounts. */
	private static final String HIDDEN_TESTS_ENV = "HIDDEN_TESTS";

	/** Default command that launches the shim server inside the sandbox. */
	private static final String SHIM_DEFAULT_COMMAND = "node " + SHIM_RUNTIME_MOUNT + "/server.js";

	/**
	 * Directory name prefix of the shared socket directory, created under the storage
	 * temp directory.
	 */
	private static final String SHIM_SOCKET_DIR_PREFIX = "gitgrader-shim-";

	/** Socket directory mode: the suite and the sandbox may not share a uid. */
	private static final String SHIM_SOCKET_DIR_MODE = "rwxrwxrwx";

	private static final Logger logger = LoggerFactory.getLogger(ShimmedGradingContainer.class);

	private final DockerClient dockerClient;

	private final GradingProperties.Docker docker;

	private final StorageProperties storage;

	ShimmedGradingContainer(DockerClient dockerClient, GradingProperties.Docker docker, StorageProperties storage) {
		this.dockerClient = dockerClient;
		this.docker = docker;
		this.storage = storage;
	}

	static boolean isShimmed(GradingExecutionRequest request) {
		String shimKind = request.shimKind();
		return shimKind != null && !shimKind.isBlank();
	}

	/**
	 * Creates a world-writable directory for the Unix socket the two containers share.
	 *
	 * <p>
	 * The sandbox and the suite run as the same user (never root), so a socket inside a
	 * directory readable by that one uid would not connect to itself from the suite; the
	 * mount is world-writable precisely because the two containers must not share an
	 * identity to share a file.
	 * @return the created directory
	 * @throws IOException when the directory cannot be created
	 */
	Path createSocketDirectory() throws IOException {
		Path dir = Files.createTempDirectory(this.storage.temp(), SHIM_SOCKET_DIR_PREFIX);
		try {
			Files.setPosixFilePermissions(dir, PosixFilePermissions.fromString(SHIM_SOCKET_DIR_MODE));
		}
		catch (UnsupportedOperationException | IOException ex) {
			// A filesystem without POSIX modes still binds; documented to need the
			// permissions on Linux, where this project's deployment runs.
			logger.warn("Could not set {} on socket directory {}; the two containers may not share the socket",
					SHIM_SOCKET_DIR_MODE, dir, ex);
		}
		return dir;
	}

	/**
	 * Removes the socket directory and everything in it, best effort.
	 * @param socketDir the shared socket directory, or {@code null} if none was created
	 */
	void deleteSocketDirectory(Path socketDir) {
		if (socketDir == null) {
			return;
		}
		try {
			// The socket file itself is owned by the sandbox's uid, so the directory
			// must be emptied before it can be removed.
			try (var files = Files.list(socketDir)) {
				for (Path file : files.toList()) {
					try {
						Files.deleteIfExists(file);
					}
					catch (IOException ex) {
						logger.warn("Failed to delete socket file {}", file, ex);
					}
				}
			}
			Files.deleteIfExists(socketDir);
		}
		catch (IOException ex) {
			logger.warn("Failed to clean up shim socket directory {}", socketDir, ex);
		}
	}

	/**
	 * Creates the sandbox side of a shimmed run: the submission's workspace, the shared
	 * socket, and none of the hidden tests. Package-private for testing.
	 * @param request the graded shim request
	 * @param socketDir the shared socket directory, its path already resolved on the host
	 * @return the configured create container command
	 */
	CreateContainerCmd createSandbox(GradingExecutionRequest request, Path socketDir) {
		List<Bind> binds = new ArrayList<>();
		binds.add(new Bind(SandboxConfig.hostWorkspace(this.docker, request), new Volume("/workspace"), AccessMode.rw));
		binds.add(new Bind(hostSocketDirectory(socketDir), new Volume(SHIM_SOCKET_VOLUME), AccessMode.rw));
		withShimBind(binds);
		HostConfig hostConfig = SandboxConfig.baseHostConfig(this.docker, request);
		hostConfig.withBinds(binds.toArray(new Bind[0]));

		List<String> env = new ArrayList<>();
		request.environment().forEach((k, v) -> {
			// Whatever the suite needs, the sandbox does not get; the hidden tests are
			// the whole reason this container is separate.
			if (!HIDDEN_TESTS_ENV.equals(k)) {
				env.add(k + "=" + v);
			}
		});
		env.add(SHIM_SOCKET_ENV + "=" + SHIM_SOCKET_PATH);

		String commandStr = shimCommand(request);
		String installCommand = request.installCommand();
		if (installCommand != null && !installCommand.isBlank()) {
			commandStr = installCommand + " && " + commandStr;
		}

		return this.dockerClient.createContainerCmd(request.runtimeImageDigest())
			.withHostConfig(hostConfig)
			.withUser(this.docker.user())
			.withWorkingDir("/workspace")
			.withEnv(env)
			.withCmd(List.of("sh", "-c", commandStr));
	}

	/**
	 * Creates the suite side of a shimmed run: the hidden tests, the shared socket, and
	 * none of the submission. Package-private for testing.
	 * @param request the graded shim request
	 * @param socketDir the shared socket directory, its path already resolved on the host
	 * @return the configured create container command
	 */
	CreateContainerCmd createSuite(GradingExecutionRequest request, Path socketDir) {
		List<Bind> binds = new ArrayList<>();
		binds.add(new Bind(SandboxConfig.hostHiddenTests(this.docker, this.storage, request),
				new Volume("/opt/hidden-tests"), AccessMode.ro));
		binds.add(new Bind(hostSocketDirectory(socketDir), new Volume(SHIM_SOCKET_VOLUME), AccessMode.rw));
		withShimBind(binds);
		HostConfig hostConfig = SandboxConfig.baseHostConfig(this.docker, request);
		hostConfig.withBinds(binds.toArray(new Bind[0]));

		List<String> env = new ArrayList<>();
		request.environment().forEach((k, v) -> env.add(k + "=" + v));
		env.add(SHIM_SOCKET_ENV + "=" + SHIM_SOCKET_PATH);

		return this.dockerClient.createContainerCmd(request.runtimeImageDigest())
			.withHostConfig(hostConfig)
			.withUser(this.docker.user())
			.withWorkingDir("/workspace")
			.withEnv(env)
			.withCmd(List.of("sh", "-c", request.testCommand()));
	}

	/**
	 * Resolves where the shared socket directory lives as the Docker daemon sees it.
	 * @param socketDir the directory created under the storage temp directory
	 * @return the path to bind, translated onto the host when a root is configured
	 */
	private String hostSocketDirectory(Path socketDir) {
		Path name = socketDir.getFileName();
		if (name == null) {
			throw new IllegalStateException("Shim socket directory has no name: " + socketDir);
		}
		String mountRoot = this.docker.workspaceMountRoot();
		return mountRoot.isEmpty() ? socketDir.toAbsolutePath().toString() : mountRoot + "/" + name;
	}

	/**
	 * Adds the shim source to a container when the deployment runs on images that do not
	 * have it built in yet; an empty {@code shimMountPath} leaves it to the image.
	 * @param binds the bind list to append to
	 */
	private void withShimBind(List<Bind> binds) {
		String source = this.docker.shimMountPath();
		if (source.isBlank()) {
			return;
		}
		binds.add(new Bind(source, new Volume(SHIM_RUNTIME_MOUNT), AccessMode.ro));
	}

	/**
	 * The command that starts the protocol server inside the sandbox.
	 * @param request the graded shim request
	 * @return the request's command, or the canonical one for the runtime images
	 */
	private static String shimCommand(GradingExecutionRequest request) {
		String command = request.shimCommand();
		return (command == null || command.isBlank()) ? SHIM_DEFAULT_COMMAND : command;
	}

}
