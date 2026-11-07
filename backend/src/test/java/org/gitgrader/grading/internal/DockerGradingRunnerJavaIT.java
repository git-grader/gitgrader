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
 * Drives the real Docker grading runner against the locally rebuilt Java runtime images,
 * the way a submission to the server is graded. The shim is baked into
 * {@code gitgrader-java-25:local} and {@code gitgrader-java-27:local} (no host mount, no
 * {@code SOLUTION_PATH}), so the sandbox must compile the single {@code Solution.java}
 * under {@code /workspace/src} and serve its public static methods, while the suite
 * compiles a JUnit class against the baked console jar and reports through TapRunner.
 *
 * <p>
 * Like {@code DockerGradingRunnerWbeIT}, this test assumes the images away when a clone
 * has not built them: CI does not build the runtime images, so the suite is skipped there
 * rather than failed.
 */
@EnabledIfDockerAvailable
class DockerGradingRunnerJavaIT {

	private static final String SOLUTION = """
			public class Solution {
				public static String truncate(String text, int length) {
					return text.length() <= length ? text : text.substring(0, length);
				}
				public static String upper(String text) {
					return text == null ? null : text.toUpperCase();
				}
				public static int add(int a, int b) {
					return a + b;
				}
			}
			""";

	private static final String HIDDEN_SUITE = """
			import org.junit.jupiter.api.DisplayName;
			import org.junit.jupiter.api.Test;

			import static org.junit.jupiter.api.Assertions.assertEquals;

			class SolutionTest {

				private final ShimClient shim = ShimClient.connect();

				@org.junit.jupiter.api.AfterAll
				static void closeClient() {
					// sockets clean up with the container; nothing to force
				}

				@Test
				@DisplayName("truncate keeps short text and cuts long text to the length")
				void truncate() {
					assertEquals("exact", shim.call("truncate", "exact", 5));
					assertEquals("abc", shim.call("truncate", "abcdef", 3));
				}

				@Test
				@DisplayName("upper uppercases the text")
				void upper() {
					assertEquals("HELLO", shim.call("upper", "hello"));
				}

				@Test
				@DisplayName("add sums two integers")
				void add() {
					assertEquals(5, shim.call("add", 2, 3));
				}
			}
			""";

	private static final String TEST_COMMAND = "cd /opt/hidden-tests"
			+ " && javac -cp /opt/junit-platform-console-standalone.jar:/opt/gitgrader-shim -d /tmp/classes *.java"
			+ " && java -cp /tmp/classes:/opt/gitgrader-shim:/opt/junit-platform-console-standalone.jar TapRunner";

	@Test
	@DisplayName("grades a complete Java solution 3 of 3 through the real runner")
	void gradesACompleteSolutionOnJava25(@TempDir Path tempDir) throws IOException, InterruptedException {
		assertJavaImageGrades("gitgrader-java-25:local", tempDir);
	}

	@Test
	@DisplayName("grades the same Java solution on the Java 27 runtime")
	void gradesACompleteSolutionOnJava27(@TempDir Path tempDir) throws IOException, InterruptedException {
		assertJavaImageGrades("gitgrader-java-27:local", tempDir);
	}

	private static void assertJavaImageGrades(String image, Path tempDir) throws IOException, InterruptedException {
		GradingProperties properties = properties();
		DockerClient client = new DockerClientConfiguration().dockerClient(properties);
		Assumptions.assumeTrue(imagePresent(client, image), image + " is not built");
		StorageProperties storage = storageIn(tempDir);

		Path workspace = tempDir.resolve("workspace");
		Files.createDirectories(workspace.resolve("src"));
		Files.writeString(workspace.resolve("src/Solution.java"), SOLUTION);
		makeWorldReadable(workspace);

		Path suite = tempDir.resolve("suite");
		Files.createDirectories(suite);
		Files.writeString(suite.resolve("SolutionTest.java"), HIDDEN_SUITE);

		GradingResult result = new DockerGradingRunner(client, properties, Clock.systemUTC(), storage,
				(imageRef) -> Optional.empty())
			.execute(new GradingExecutionRequest(workspace, suite, image, null, TEST_COMMAND, Duration.ofMinutes(3),
					DataSize.ofMegabytes(512).toBytes(), 1.0, 256, false, DataSize.ofMegabytes(1).toBytes(),
					"corr-java", java.util.Map.of(), "java-ipc", "java -cp /opt/gitgrader-shim Server"));

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
