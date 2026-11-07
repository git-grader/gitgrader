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
 * Drives the real Docker grading runner against the locally rebuilt Python runtime image,
 * the way a submission to the server is graded. The shim is baked into
 * {@code gitgrader-python-3.14:local} (no host mount, no {@code SOLUTION_PATH}), so the
 * sandbox must import the single {@code solution.py} under {@code /workspace/src} and
 * serve its module-level functions, while the suite runs pytest through the baked
 * {@code tap_runner.py}, which reports TAP in manifest order.
 *
 * <p>
 * Like {@code DockerGradingRunnerWbeIT}, this test assumes the image away when a clone
 * has not built it: CI does not build the runtime images, so the suite is skipped there
 * rather than failed.
 */
@EnabledIfDockerAvailable
class DockerGradingRunnerPythonIT {

	private static final String IMAGE = "gitgrader-python-3.14:local";

	private static final String SOLUTION = """
			def truncate(text, max_length):
			    return text if len(text) <= max_length else text[:max_length]

			def upper(text):
			    return None if text is None else text.upper()

			def add(a, b):
			    return a + b
			""";

	private static final String HIDDEN_SUITE = """
			import sys

			sys.path.insert(0, "/opt/gitgrader-shim")

			from client import create_shim_client

			shim = create_shim_client()

			def test_truncate():
			    assert shim.truncate("exact", 5) == "exact"
			    assert shim.truncate("abcdef", 3) == "abc"

			def test_upper():
			    assert shim.upper("hello") == "HELLO"

			def test_add():
			    assert shim.add(2, 3) == 5
			""";

	private static final String MANIFEST = """
			{
			  "suiteKey": "python-it",
			  "version": "1.0.0",
			  "tests": [
			    { "id": "truncate", "name": "truncate keeps short text and cuts long text to the length", "weight": 1 },
			    { "id": "upper", "name": "upper uppercases the text", "weight": 1 },
			    { "id": "add", "name": "add sums two integers", "weight": 1 }
			  ]
			}
			""";

	private static final String TEST_COMMAND = "cd /opt/hidden-tests && python3 /opt/gitgrader-shim/tap_runner.py";

	@Test
	@DisplayName("grades a complete Python solution 3 of 3 through the real runner")
	void gradesACompleteSolution(@TempDir Path tempDir) throws IOException, InterruptedException {
		GradingProperties properties = properties();
		DockerClient client = new DockerClientConfiguration().dockerClient(properties);
		Assumptions.assumeTrue(imagePresent(client, IMAGE), IMAGE + " is not built");
		StorageProperties storage = storageIn(tempDir);

		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace.resolve("src"));
		Files.writeString(workspace.resolve("src/solution.py"), SOLUTION);
		makeWorldReadable(workspace);

		Path suite = tempDir.resolve("suite");
		Files.createDirectories(suite);
		Files.writeString(suite.resolve("test_hidden.py"), HIDDEN_SUITE);
		Files.writeString(suite.resolve("manifest.json"), MANIFEST);
		makeWorldReadable(suite);

		GradingResult result = new DockerGradingRunner(client, properties, Clock.systemUTC(), storage,
				(imageRef) -> Optional.empty())
			.execute(new GradingExecutionRequest(workspace, suite, IMAGE, null, TEST_COMMAND, Duration.ofMinutes(3),
					DataSize.ofMegabytes(512).toBytes(), 1.0, 256, false, DataSize.ofMegabytes(1).toBytes(),
					"corr-python", java.util.Map.of(), "python-ipc", "python3 /opt/gitgrader-shim/server.py"));

		assertThat(result.infrastructureFailure()).as("the run itself must succeed: %s", result.failureDetail())
			.isFalse();
		assertThat(result.timedOut()).isFalse();
		assertThat(result.stdout()).contains("1..3").contains("# tests 3").contains("# pass 3").contains("# fail 0");
		assertThat(result.exitCode()).isZero();
	}

	private static boolean imagePresent(DockerClient client, String image) {
		try {
			client.inspectImageCmd(image).exec();
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
