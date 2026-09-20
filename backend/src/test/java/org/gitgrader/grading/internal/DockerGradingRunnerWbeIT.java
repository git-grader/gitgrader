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
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.exception.NotFoundException;
import org.gitgrader.configuration.GradingProperties;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.grading.GradingExecutionRequest;
import org.gitgrader.grading.GradingResult;
import org.gitgrader.testsupport.EnabledIfDockerAvailable;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Drives the real Docker grading runner against the WBE exercises, the way a submission
 * to the server is graded. The image is the locally rebuilt
 * {@code gitgrader-node-24:local} runtime: the shim is baked in (no host mount, no
 * {@code SOLUTION_PATH}), so the sandbox must auto-discover the module from
 * {@code /workspace/src}.
 *
 * <p>
 * The WBE tree is ignored, so this test assumes the fixtures away on a clone that does
 * not carry them, exactly as {@code DockerGradingRunnerIT} does for the worked example.
 */
@EnabledIfDockerAvailable
class DockerGradingRunnerWbeIT {

	private static final String IMAGE = "gitgrader-node-24:local";

	private static final Path WBE_01 = Path.of("..", "assignments", "wbe", "wbe-01-power");

	private static final String TEST_COMMAND = "cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs";

	@Test
	@DisplayName("grades a complete WBE submission 10 of 10 through the real runner")
	void gradesACompleteSubmission(@TempDir Path tempDir) throws IOException, InterruptedException {
		GradingResult result = round(tempDir, "reference-solution/src/power.js");

		assertThat(result.infrastructureFailure()).as("the run itself must succeed: %s", result.failureDetail())
			.isFalse();
		assertThat(result.timedOut()).isFalse();
		assertThat(result.stdout()).contains("1..10").contains("# tests 10").contains("# pass 10").contains("# fail 0");
		assertThat(result.exitCode()).isZero();
	}

	@Test
	@DisplayName("attributes a stub submission's failures instead of scoring it zero")
	void gradesAStubSubmission(@TempDir Path tempDir) throws IOException, InterruptedException {
		GradingResult result = round(tempDir, null);

		assertThat(result.infrastructureFailure()).as("the run itself must succeed: %s", result.failureDetail())
			.isFalse();
		assertThat(result.timedOut()).isFalse();
		// Every declared test is reported by name; the reject-contract checks pass on a
		// stub while the behaviour checks fail. Nothing is skipped or scored zero.
		assertThat(result.stdout()).contains("1..10").contains("# tests 10").contains("# pass 5").contains("# fail 5");
		assertThat(result.exitCode()).isNotZero();
	}

	private static GradingResult round(Path tempDir, String referenceSolution) throws IOException {
		Assumptions.assumeTrue(Files.isDirectory(WBE_01), "WBE assignment tree is not present");

		GradingProperties properties = properties();
		DockerClient client = new DockerClientConfiguration().dockerClient(properties);
		Assumptions.assumeTrue(imagePresent(client), "gitgrader-node-24:local is not built");
		StorageProperties storage = storageIn(tempDir);

		Path workspace = tempDir.resolve("workspace");
		copyDirectory(WBE_01.resolve("template"), workspace);
		if (referenceSolution != null) {
			Files.copy(WBE_01.resolve(referenceSolution), workspace.resolve("src/power.js"),
					java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		makeWorldReadable(workspace);

		Path suite = tempDir.resolve("suite");
		copyDirectory(WBE_01.resolve("hidden-tests"), suite);

		return new DockerGradingRunner(client, properties, Clock.systemUTC(), storage, (image) -> Optional.empty())
			.execute(new GradingExecutionRequest(workspace, suite, IMAGE, null, TEST_COMMAND, Duration.ofMinutes(3),
					DataSize.ofMegabytes(512).toBytes(), 1.0, 256, false, DataSize.ofMegabytes(1).toBytes(), "corr-wbe",
					java.util.Map.of(), "node-ipc", null));
	}

	private static boolean imagePresent(DockerClient client) {
		try {
			client.inspectImageCmd(IMAGE).exec();
			return true;
		}
		catch (NotFoundException ex) {
			return false;
		}
	}

	private static GradingProperties properties() {
		return new GradingProperties("docker", 2, Duration.ofSeconds(120), DataSize.ofMegabytes(512), 1.0, 256, false,
				DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true, ""),
				new GradingProperties.RunnerApi(false, "", "", Duration.ofSeconds(10), Duration.ofSeconds(30)),
				new GradingProperties.Queue(true, Duration.ofSeconds(2), Duration.ofMinutes(15), 3,
						Duration.ofSeconds(30), 3, 500, 1000, Duration.ofSeconds(30)));
	}

	private static StorageProperties storageIn(Path tempDir) throws IOException {
		Path tmp = tempDir.resolve("tmp");
		Files.createDirectories(tmp);
		return new StorageProperties(tempDir.resolve("repositories").toString(),
				tempDir.resolve("templates").toString(), tempDir.resolve("tests").toString(),
				tempDir.resolve("artifacts").toString(), tmp.toString());
	}

	private static void copyDirectory(Path source, Path target) throws IOException {
		try (Stream<Path> entries = Files.walk(source)) {
			for (Path entry : entries.sorted().toList()) {
				Path destination = target.resolve(source.relativize(entry).toString());
				if (Files.isDirectory(entry)) {
					Files.createDirectories(destination);
				}
				else {
					Files.createDirectories(destination.getParent());
					Files.copy(entry, destination);
				}
			}
		}
	}

	private static void makeWorldReadable(Path root) throws IOException {
		try (Stream<Path> entries = Files.walk(root)) {
			for (Path entry : entries.sorted(Comparator.reverseOrder()).toList()) {
				Set<java.nio.file.attribute.PosixFilePermission> permissions = Files.isDirectory(entry)
						? java.nio.file.attribute.PosixFilePermissions.fromString("rwxr-xr-x")
						: java.nio.file.attribute.PosixFilePermissions.fromString("rw-r--r--");
				try {
					Files.setPosixFilePermissions(entry, permissions);
				}
				catch (UnsupportedOperationException ex) {
					return;
				}
			}
		}
	}

}
