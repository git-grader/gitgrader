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
import java.time.Duration;
import java.util.Map;

import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;

/**
 * Reduces what a caller asks for to what the runner is willing to do.
 *
 * <p>
 * The web tier no longer holds the Docker socket, but it does get to say "grade this", so
 * the value of that boundary is exactly how little the request is allowed to decide. A
 * caller that has been taken over would otherwise name a path outside the volumes, ask
 * for the whole host's memory, or wait forever.
 *
 * <p>
 * Everything the sandbox is hardened with - no network, dropped capabilities, a read-only
 * root, the unprivileged user - is not in the request at all. It comes from the runner's
 * own configuration, so there is nothing there for a caller to turn off.
 */
class RunnerRequestGuard {

	private final GradingProperties properties;

	private final StorageProperties storage;

	RunnerRequestGuard(GradingProperties properties, StorageProperties storage) {
		this.properties = properties;
		this.storage = storage;
	}

	/**
	 * Rewrites a request so it can only reach this runner's volumes and limits.
	 * @param request what the caller asked for
	 * @return what the runner will actually run
	 * @throws IllegalArgumentException when the request names a path outside the volumes
	 */
	GradingExecutionRequest sanitise(GradingExecutionRequest request) {
		Path workspace = inside(this.storage.temp(), request.workspaceDirectory(), "workspace");
		Path hiddenTests = inside(this.storage.tests(), request.hiddenTestsDirectory(), "hidden tests");

		return new GradingExecutionRequest(workspace, hiddenTests, request.runtimeImageDigest(),
				request.installCommand(), request.testCommand(), atMost(request.timeout()),
				Math.min(request.memoryLimitBytes(), this.properties.defaultMemoryLimit().toBytes()),
				Math.min(request.cpuLimit(), this.properties.defaultCpuLimit()),
				Math.min(request.pidLimit(), this.properties.defaultPidLimit()),
				// Never widened by a caller. Network access is the difference between a
				// sandbox and a host on the internal network.
				request.networkEnabled() && this.properties.networkEnabled(),
				Math.min(request.logSizeLimitBytes(), this.properties.logSizeLimit().toBytes()),
				request.correlationId(), Map.copyOf(request.environment()));
	}

	private Duration atMost(Duration requested) {
		Duration ceiling = this.properties.defaultTimeout();
		return (requested.isZero() || requested.isNegative() || requested.compareTo(ceiling) > 0) ? ceiling : requested;
	}

	/**
	 * Resolves a caller's path against the volume it is supposed to name.
	 *
	 * <p>
	 * The same guard the rest of the application resolves stored paths through, applied
	 * here to a path that arrives over the network rather than out of the database.
	 * @param root the volume the path must stay inside
	 * @param candidate the path the caller sent
	 * @param what the name used when refusing, for the operator reading the log
	 * @return the resolved path
	 */
	private static Path inside(Path root, Path candidate, String what) {
		Path absoluteRoot = root.toAbsolutePath().normalize();
		Path resolved = candidate.toAbsolutePath().normalize();
		if (!resolved.startsWith(absoluteRoot)) {
			throw new IllegalArgumentException("The " + what + " path is outside the runner's volume");
		}
		return resolved;
	}

}
