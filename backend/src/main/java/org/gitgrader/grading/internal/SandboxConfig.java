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
import java.util.List;
import java.util.Map;

import com.github.dockerjava.api.model.Capability;
import com.github.dockerjava.api.model.HostConfig;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;

/**
 * The shared description of a grading container, whichever half of a run it is.
 *
 * <p>
 * Every container that executes untrusted code gets the same hardening - no swap, no
 * extra capabilities, a bounded pid space - and the paths that reach it from the host are
 * resolved on the host. Single-container runs and both halves of a shimmed run build
 * their containers from these two services.
 */
final class SandboxConfig {

	/** Linux CFS scheduling period in microseconds; one full period equals one CPU. */
	private static final long CPU_PERIOD_MICROS = 100_000L;

	/**
	 * Floor on the quota so a very small cpu-limit cannot round down to no CPU at all.
	 */
	private static final long MINIMUM_CPU_QUOTA_MICROS = 1_000L;

	private SandboxConfig() {
	}

	/**
	 * The security posture of a grading container, whichever side it is.
	 * @param docker the runner's Docker configuration
	 * @param request the grading execution request
	 * @return the base host configuration
	 */
	static HostConfig baseHostConfig(GradingProperties.Docker docker, GradingExecutionRequest request) {
		HostConfig hostConfig = HostConfig.newHostConfig()
			.withAutoRemove(true)
			.withReadonlyRootfs(docker.readOnlyRootFilesystem())
			.withTmpFs(Map.of("/tmp", "size=" + docker.tmpfsSize().toBytes()))
			.withMemory(request.memoryLimitBytes())
			// Docker reads an unset swap limit as twice the memory limit, so a memory
			// ceiling on its own is not one: a submission that allocates past it is
			// swapped rather than killed, and gets double what the assignment allowed at
			// the cost of the host's disk. Equal values leave the container no swap.
			.withMemorySwap(request.memoryLimitBytes())
			.withCpuQuota(Math.max(MINIMUM_CPU_QUOTA_MICROS, (long) (request.cpuLimit() * CPU_PERIOD_MICROS)))
			.withCpuPeriod(CPU_PERIOD_MICROS)
			.withPidsLimit((long) request.pidLimit());

		if (docker.noNewPrivileges()) {
			hostConfig.withSecurityOpts(List.of("no-new-privileges=true"));
		}

		if (docker.dropAllCapabilities()) {
			hostConfig.withCapDrop(Capability.ALL);
		}

		if (!request.networkEnabled()) {
			hostConfig.withNetworkMode("none");
		}

		return hostConfig;
	}

	/**
	 * Resolves where the submitted workspace lives as the Docker daemon sees it.
	 * @param docker the runner's Docker configuration
	 * @param request the execution request
	 * @return the path to bind, translated onto the host when a root is configured
	 */
	static String hostWorkspace(GradingProperties.Docker docker, GradingExecutionRequest request) {
		String hostWorkspace = request.workspaceDirectory().toAbsolutePath().toString();
		String mountRoot = docker.workspaceMountRoot();
		if (!mountRoot.isEmpty()) {
			Path workspaceName = request.workspaceDirectory().getFileName();
			if (workspaceName == null) {
				throw new IllegalStateException(
						"Grading workspace path has no directory name: " + request.workspaceDirectory());
			}
			hostWorkspace = mountRoot + "/" + workspaceName;
		}
		return hostWorkspace;
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
	 * @param docker the runner's Docker configuration
	 * @param storage the storage layout
	 * @param request the execution request
	 * @return the path to bind, translated onto the host when a root is configured
	 */
	static String hostHiddenTests(GradingProperties.Docker docker, StorageProperties storage,
			GradingExecutionRequest request) {
		Path tests = request.hiddenTestsDirectory().toAbsolutePath();
		String mountRoot = docker.testsMountRoot();
		if (mountRoot.isEmpty()) {
			return tests.toString();
		}
		return mountRoot + "/" + storage.tests().relativize(tests);
	}

}
