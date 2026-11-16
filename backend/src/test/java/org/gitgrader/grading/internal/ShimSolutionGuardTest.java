/*
 * Copyright the GitGrader contributors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import java.time.Duration;
import java.util.Map;
import java.util.Optional;

import org.gitgrader.grading.GradingExecutionRequest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the runner refuses before starting a container because the shim could not choose
 * the submitted module.
 *
 * <p>
 * The guard mirrors the shims' own resolution rule: an existing {@code SOLUTION_PATH}
 * wins, otherwise exactly one module directly under {@code src/} is loadable.
 */
class ShimSolutionGuardTest {

	@TempDir
	Path workspace;

	@Test
	@DisplayName("ignores a single-container run however many modules it holds")
	void ignoresLegacyRun() throws IOException {
		writeModules("a.js", "b.js");

		assertThat(ShimSolutionGuard.unusableSubmission(request(Map.of(), null))).isEmpty();
	}

	@Test
	@DisplayName("accepts the single module in src/")
	void acceptsTheSingleModule() throws IOException {
		writeModules("power.js");

		assertThat(ShimSolutionGuard.unusableSubmission(request(Map.of(), "node-ipc"))).isEmpty();
	}

	@Test
	@DisplayName("names the modules when src/ holds several")
	void refusesSeveralModules() throws IOException {
		writeModules("scripts-data.js", "scripts.js");

		Optional<String> refusal = ShimSolutionGuard.unusableSubmission(request(Map.of(), "node-ipc"));

		assertThat(refusal).isPresent();
		assertThat(refusal.orElseThrow()).contains("holds 2 modules")
			.contains("scripts-data.js")
			.contains("scripts.js")
			.contains("/workspace/src")
			.contains("SOLUTION_PATH");
	}

	@Test
	@DisplayName("reports when src/ holds no module at all")
	void refusesEmptySrc() throws IOException {
		Files.writeString(Files.createDirectories(this.workspace.resolve("src")).resolve("notes.txt"), "no code\n");

		Optional<String> refusal = ShimSolutionGuard.unusableSubmission(request(Map.of(), "node-ipc"));

		assertThat(refusal).isPresent();
		assertThat(refusal.orElseThrow()).contains("holds no .js").contains("/workspace/src");
	}

	@Test
	@DisplayName("ignores a workspace without a src/ directory, which cannot be judged from here")
	void ignoresMissingSrc() {
		assertThat(ShimSolutionGuard.unusableSubmission(request(Map.of(), "node-ipc"))).isEmpty();
	}

	@Test
	@DisplayName("lets an explicit SOLUTION_PATH naming an existing file win over several modules")
	void explicitSolutionPathWins() throws IOException {
		writeModules("scripts.js", "scripts-data.js");
		Files.writeString(this.workspace.resolve("src").resolve("main.js"), "export const main = 1;\n");

		Map<String, String> env = Map.of("SOLUTION_PATH", "/workspace/src/main.js");

		assertThat(ShimSolutionGuard.unusableSubmission(request(env, "node-ipc"))).isEmpty();
	}

	@Test
	@DisplayName("still refuses when SOLUTION_PATH names a file that does not exist")
	void missingExplicitSolutionPathDoesNotCount() throws IOException {
		writeModules("scripts.js", "scripts-data.js");

		Map<String, String> env = Map.of("SOLUTION_PATH", "/workspace/src/absent.js");

		assertThat(ShimSolutionGuard.unusableSubmission(request(env, "node-ipc")).orElseThrow())
			.contains("holds 2 modules");
	}

	@Test
	@DisplayName("does not let SOLUTION_PATH escape the workspace")
	void explicitSolutionPathOutsideTheWorkspaceDoesNotCount() throws IOException {
		writeModules("scripts.js", "scripts-data.js");

		Map<String, String> env = Map.of("SOLUTION_PATH", "/workspace/../../etc/passwd");

		assertThat(ShimSolutionGuard.unusableSubmission(request(env, "node-ipc"))).isPresent();
	}

	@Test
	@DisplayName("counts only the shim family's own extension")
	void countsOnlyTheFamilysExtension() throws IOException {
		Files.createDirectories(this.workspace.resolve("src"));
		Files.writeString(this.workspace.resolve("src").resolve("solution.py"), "def f():\n    return 1\n");
		Files.writeString(this.workspace.resolve("src").resolve("helper.py"), "def g():\n    return 2\n");
		Files.writeString(this.workspace.resolve("src").resolve("readme.js"), "not a python module\n");

		Optional<String> refusal = ShimSolutionGuard.unusableSubmission(request(Map.of(), "python-ipc"));

		assertThat(refusal).isPresent();
		assertThat(refusal.orElseThrow()).contains("holds 2 modules").contains("solution.py").contains("helper.py");
	}

	@Test
	@DisplayName("counts only files directly under src/, never nested ones")
	void countsDirectChildrenOnly() throws IOException {
		Path src = Files.createDirectories(this.workspace.resolve("src"));
		Files.writeString(src.resolve("scripts.js"), "export const a = 1;\n");
		Files.writeString(Files.createDirectories(src.resolve("nested")).resolve("other.js"), "export const b = 2;\n");

		assertThat(ShimSolutionGuard.unusableSubmission(request(Map.of(), "node-ipc"))).isEmpty();
	}

	@Test
	@DisplayName("leaves an unknown shim family to the shim's own startup error")
	void ignoresUnknownShimFamily() throws IOException {
		writeModules("a.rb", "b.rb");

		assertThat(ShimSolutionGuard.unusableSubmission(request(Map.of(), "ruby-ipc"))).isEmpty();
	}

	private void writeModules(String... names) throws IOException {
		Path src = Files.createDirectories(this.workspace.resolve("src"));
		for (String name : names) {
			Files.writeString(src.resolve(name), "export const x = 1;\n");
		}
	}

	private GradingExecutionRequest request(Map<String, String> environment, String shimKind) {
		return new GradingExecutionRequest(this.workspace, Path.of("/data/tests/suite"), "image@sha256:1234", null,
				"npm test", Duration.ofSeconds(30), 1024L * 1024 * 256, 1.0, 128, false, 1024 * 1024, "corr-1",
				environment, shimKind, null);
	}

}
