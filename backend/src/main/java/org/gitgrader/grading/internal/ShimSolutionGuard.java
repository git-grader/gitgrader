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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.jspecify.annotations.Nullable;

/**
 * Fails a shimmed run before any container starts when the submitted workspace cannot
 * name the single module the shim server would load.
 *
 * <p>
 * The shim servers deliberately refuse to guess between several modules in
 * {@code /workspace/src} and exit at once, so the suite then spends its connect deadline
 * on a socket that never appears. What reaches the results table is a graded run full of
 * connection errors - or a bare timeout - rather than an infrastructure error naming the
 * files. This guard applies the shim's own resolution rule on the host side, where the
 * workspace can still be read, and turns that case into an immediate refusal.
 *
 * <p>
 * The rule mirrors the shims exactly: an explicit {@code SOLUTION_PATH} that names an
 * existing file inside the workspace wins; otherwise exactly one module file directly
 * under {@code src/} is loadable. Only the shim families this build knows are checked; an
 * unknown family keeps its current behaviour and the shim's own startup error remains the
 * report. A workspace that cannot be inspected from here - a runner service grading on
 * another host sees only its own paths - is likewise left to the shim.
 */
final class ShimSolutionGuard {

	/**
	 * Where the workspace is mounted inside the sandbox; the shims' conventional src/
	 * sits under it.
	 */
	private static final String WORKSPACE_MOUNT = "/workspace";

	/** Directory in the workspace the shims scan for the submitted module. */
	private static final String SRC_DIRECTORY = "src";

	/**
	 * Environment variable that names the submitted module explicitly, as the shims read
	 * it.
	 */
	private static final String SOLUTION_PATH_ENV = "SOLUTION_PATH";

	private ShimSolutionGuard() {
	}

	/**
	 * Reports why the submission cannot be served by a shim, mirroring the shim's own
	 * resolution rule.
	 * @param request the graded request
	 * @return why the run cannot proceed, or empty when the submission names a module
	 */
	static Optional<String> unusableSubmission(GradingExecutionRequest request) {
		if (!ShimmedGradingContainer.isShimmed(request)) {
			// A single-container run has no shim to satisfy; the test command decides
			// what to run and may happily use several files.
			return Optional.empty();
		}
		String extension = moduleExtension(request.shimKind());
		if (extension == null || explicitModuleExists(request)) {
			// An unknown shim family keeps the shim's own error; an explicit
			// SOLUTION_PATH is loaded as-is without consulting src/.
			return Optional.empty();
		}
		return describeIfUnusable(request, extension);
	}

	/**
	 * Applies the src/ rule to a shimmed request of a known family.
	 * @param request the graded request
	 * @param extension the module extension of the shim family
	 * @return why the submission cannot be served, or empty when src/ holds exactly one
	 * module or cannot be judged from here
	 */
	private static Optional<String> describeIfUnusable(GradingExecutionRequest request, String extension) {
		Path srcDir = request.workspaceDirectory().resolve(SRC_DIRECTORY);
		if (!Files.isDirectory(srcDir)) {
			return Optional.empty();
		}
		List<String> modules = moduleNames(srcDir, extension);
		if (modules == null || modules.size() == 1) {
			return Optional.empty();
		}
		String containerSrc = WORKSPACE_MOUNT + "/" + SRC_DIRECTORY;
		if (modules.isEmpty()) {
			return Optional.of("The submitted workspace has nothing the shim can load: " + containerSrc + " holds no "
					+ extension + " file. Put the solution in " + SRC_DIRECTORY + "/ or set " + SOLUTION_PATH_ENV
					+ " to the module to test.");
		}
		return Optional.of("The shim cannot choose the submitted solution: " + containerSrc + " holds " + modules.size()
				+ " modules (" + String.join(", ", modules) + "). Keep exactly one module in " + SRC_DIRECTORY
				+ "/ or set " + SOLUTION_PATH_ENV + " to the one to test.");
	}

	/**
	 * Maps a shim kind to the module extension its server discovers.
	 * @param shimKind the declared topology key, possibly null
	 * @return the extension, or null for a family this build does not know
	 */
	private static @Nullable String moduleExtension(@Nullable String shimKind) {
		if (shimKind == null) {
			return null;
		}
		if (shimKind.startsWith("node")) {
			return ".js";
		}
		if (shimKind.startsWith("python")) {
			return ".py";
		}
		if (shimKind.startsWith("java")) {
			return ".java";
		}
		return null;
	}

	/**
	 * Reports whether the request's {@code SOLUTION_PATH} names an existing file inside
	 * the workspace, which the shim would then load without consulting src/.
	 * @param request the graded request
	 * @return whether an explicit, existing solution module is named
	 */
	private static boolean explicitModuleExists(GradingExecutionRequest request) {
		String explicit = request.environment().get(SOLUTION_PATH_ENV);
		if (explicit == null || explicit.isBlank()) {
			return false;
		}
		String prefix = WORKSPACE_MOUNT + "/";
		if (!explicit.startsWith(prefix)) {
			return false;
		}
		Path mapped;
		try {
			mapped = StorageProperties.resolveInside(request.workspaceDirectory(), explicit.substring(prefix.length()));
		}
		catch (IllegalArgumentException ex) {
			return false;
		}
		return Files.isRegularFile(mapped);
	}

	/**
	 * Lists the module files directly under a directory, sorted by name, exactly as the
	 * shims' discovery does (direct children only, never recursive).
	 * @param srcDir the directory to scan
	 * @param extension the module extension to match
	 * @return the file names, or null when the directory cannot be read
	 */
	private static @Nullable List<String> moduleNames(Path srcDir, String extension) {
		List<String> names = new ArrayList<>();
		try (DirectoryStream<Path> entries = Files.newDirectoryStream(srcDir)) {
			for (Path entry : entries) {
				Path fileName = entry.getFileName();
				if (fileName != null && Files.isRegularFile(entry) && fileName.toString().endsWith(extension)) {
					names.add(fileName.toString());
				}
			}
		}
		catch (IOException ex) {
			// An unreadable src/ cannot be counted; the shim will report it itself.
			return null;
		}
		names.sort(String::compareTo);
		return names;
	}

}
