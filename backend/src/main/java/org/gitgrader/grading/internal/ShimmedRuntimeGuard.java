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
import java.util.UUID;

import org.gitgrader.runtimes.RuntimeView;
import org.gitgrader.runtimes.ShimTopology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Decides whether a runtime may grade a given hidden suite.
 *
 * <p>
 * Kept apart from the executor because this is a judgement about the pair of a runtime
 * and a suite, not about building a sandbox request, and because both answers are
 * consequential enough to deserve one place: one refuses the run, the other only says so.
 * </p>
 */
final class ShimmedRuntimeGuard {

	private static final Logger logger = LoggerFactory.getLogger(ShimmedRuntimeGuard.class);

	private ShimmedRuntimeGuard() {
	}

	/**
	 * Refuses a shimmed runtime paired with a suite that cannot reach the sandbox.
	 *
	 * <p>
	 * A shimmed runtime splits the submission and the suite into two containers, so the
	 * suite must reach the sandbox over the grading protocol to load the module at all. A
	 * suite that never connects would fail every check with a connection error before it
	 * can test anything, which is not a defensible grade; refusing is the same rule the
	 * rest of the module follows for a missing manifest, an infrastructure error rather
	 * than a mark.
	 * @param runtime the runtime the run would use
	 * @param hiddenTests the mounted hidden suite directory
	 * @throws IllegalStateException when the two cannot be graded together
	 */
	static void requireUsablePairing(RuntimeView runtime, Path hiddenTests) {
		if (ShimTopology.isDeclared(runtime.shimKind()) && !hasShimHarness(hiddenTests)) {
			throw new IllegalStateException("The runtime '" + runtime.runtimeKey() + "' is shimmed but the hidden "
					+ "test suite at " + hiddenTests
					+ " never connects to the sandbox. A suite graded in two containers must import the shim "
					+ "client (createShimClient) from /opt/gitgrader-shim/client.js; without it no check could "
					+ "reach the submission.");
		}
	}

	/**
	 * Records that a runtime is grading on the topology its row does not name.
	 *
	 * <p>
	 * Not a failure. A runtime created before the topology had to be declared has no
	 * topology on its row, and it grades correctly in one sandbox. Logged because the two
	 * topologies are not interchangeable, so an operator who believes a runtime is
	 * shimmed would otherwise have no way to notice that it is not.
	 * @param runtime the runtime the run is using
	 * @param runId the run being graded, for correlation
	 */
	static void reportUndeclaredTopology(RuntimeView runtime, UUID runId) {
		if (!ShimTopology.isDeclared(runtime.shimKind())) {
			logger.warn(
					"Runtime '{}' grades in the single-sandbox topology, which is only recorded as an absent "
							+ "topology on runtime rows created before '{}' was required. It still grades correctly; "
							+ "save the runtime to state the topology explicitly [runId={}]",
					runtime.runtimeKey(), ShimTopology.LEGACY, runId);
		}
	}

	/**
	 * Reports whether some script in the suite imports the shim client.
	 *
	 * <p>
	 * The shim client is the only thing that opens the socket, and the sandbox trusts the
	 * marker because a working suite has to write the same reference. Requiring it is
	 * what stops a shimmed runtime from silently regressing to grading in one shared
	 * process, where the submission could once again read the hidden sources (issue #40).
	 * @param hiddenTests the mounted hidden suite directory
	 * @return whether some script in the suite references the shim client
	 */
	private static boolean hasShimHarness(Path hiddenTests) {
		try (var scripts = Files.walk(hiddenTests)) {
			return scripts.filter(Files::isRegularFile)
				.filter(ShimmedRuntimeGuard::isScript)
				.anyMatch(ShimmedRuntimeGuard::referencesShimClient);
		}
		catch (IOException ex) {
			// An unreadable suite is no harness; the refusal above explains itself.
			return false;
		}
	}

	private static boolean isScript(Path path) {
		Path fileName = path.getFileName();
		if (fileName == null) {
			return false;
		}
		String name = fileName.toString();
		return name.endsWith(".js") || name.endsWith(".cjs") || name.endsWith(".mjs");
	}

	private static boolean referencesShimClient(Path script) {
		try {
			return Files.readString(script, StandardCharsets.UTF_8).contains("createShimClient");
		}
		catch (IOException ex) {
			return false;
		}
	}

}
