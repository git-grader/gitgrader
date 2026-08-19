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
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.exception.NotFoundException;
import com.github.dockerjava.api.model.PullResponseItem;
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
 * Runs the Docker grading runner against a real Docker daemon.
 *
 * <p>
 * Everything else covering this runner substitutes a mocked {@code DockerClient}, which
 * proves the container is described correctly but never that one starts. That gap is not
 * academic: the client the runner depends on had no bean defining it at all, so the
 * application could not start with its default configuration while every mocked test
 * still passed. This test therefore builds the client the same way the application does,
 * rather than constructing one for the occasion.
 *
 * <p>
 * The fixture is the reference solution that deliberately fails three of the ten hidden
 * checks, so the expected outcome is a known 7 of 10 rather than a blanket "something
 * ran". A runner that silently executed nothing would report zero failures and pass a
 * weaker assertion.
 */
@EnabledIfDockerAvailable
class DockerGradingRunnerIT {

	private static final String IMAGE = "node@sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d";

	private static final Path EXAMPLE = Path.of("..", "examples", "assignments", "assignment-01-string-utils");

	@Test
	@DisplayName("executes the hidden checks in a container and reports 7 of 10")
	void gradesThePartialSolution(@TempDir Path tempDir) throws IOException, InterruptedException {
		GradingProperties properties = properties();
		DockerClient client = new DockerClientConfiguration().dockerClient(properties);
		Assumptions.assumeTrue(Files.isDirectory(EXAMPLE), "example assignment is not present");
		// The runner never pulls: a grading run must use exactly the digest the
		// assignment
		// pinned, and silently fetching it would hide a runtime that was never published.
		// Fetching it is therefore this test's own setup rather than the runner's job.
		ensureImagePresent(client);

		Path workspace = tempDir.resolve("workspace");
		copyDirectory(EXAMPLE.resolve("template"), workspace);
		Files.copy(EXAMPLE.resolve("reference-solution/partial-70/string-utils.js"),
				workspace.resolve("src/string-utils.js"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		// The container runs as an unprivileged user that is nobody in particular, so it
		// can only read a workspace that is readable by everyone.
		makeWorldReadable(workspace);

		GradingResult result = new DockerGradingRunner(client, properties, Clock.systemUTC(),
				new StorageProperties("/data/git/repositories", "/data/templates", "/data/tests", "/data/artifacts",
						"/data/tmp"),
				(image) -> Optional.empty())
			.execute(new GradingExecutionRequest(workspace, EXAMPLE.resolve("hidden-tests").toAbsolutePath(), IMAGE,
					null, "node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js", Duration.ofMinutes(3),
					DataSize.ofMegabytes(512).toBytes(), 1.0, 256, false, DataSize.ofMegabytes(1).toBytes(), "corr-it",
					Map.of()));

		assertThat(result.infrastructureFailure()).as("the run itself must succeed: %s", result.failureDetail())
			.isFalse();
		assertThat(result.timedOut()).isFalse();
		assertThat(result.stdout()).contains("# pass 7").contains("# fail 3").contains("1..10");
		// A non-zero exit is the student's three failing checks, not a broken runner.
		assertThat(result.exitCode()).isNotZero();
	}

	@Test
	@DisplayName("proves the sandbox can see what this process writes into the workspace")
	void provesTheSandboxMounts(@TempDir Path tempDir) throws InterruptedException {
		// Docker answers a bind whose source it cannot resolve by creating an empty
		// directory, so a wrong mount root graded every submission against nothing. The
		// probe runs in its own container because a grading container cannot be trusted
		// to
		// report this: the submission controls its output and its exit status.
		GradingProperties properties = properties();
		DockerClient client = new DockerClientConfiguration().dockerClient(properties);
		ensureImagePresent(client);
		StorageProperties storage = new StorageProperties(tempDir.resolve("repositories").toString(),
				tempDir.resolve("templates").toString(), tempDir.resolve("tests").toString(),
				tempDir.resolve("artifacts").toString(), tempDir.resolve("tmp").toString());

		assertThat(new DockerSandboxMountProbe(client, properties, storage).unusableReason(IMAGE))
			.as("the daemon shares this filesystem, so the probe must find its own file")
			.isEmpty();

	}

	private static void ensureImagePresent(DockerClient client) throws InterruptedException {
		try {
			client.inspectImageCmd(IMAGE).exec();
			return;
		}
		catch (NotFoundException ex) {
			// Not cached on this machine yet, so fetch it below.
		}
		client.pullImageCmd(IMAGE)
			.exec(new ResultCallback.Adapter<PullResponseItem>())
			.awaitCompletion(5, TimeUnit.MINUTES);
	}

	private static GradingProperties properties() {
		return new GradingProperties("docker", 2, Duration.ofSeconds(120), DataSize.ofMegabytes(512), 1.0, 256, false,
				DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true),
				new GradingProperties.RunnerApi(false, "", "", Duration.ofSeconds(10), Duration.ofSeconds(30)),
				new GradingProperties.Queue(true, Duration.ofSeconds(2), Duration.ofMinutes(15), 3,
						Duration.ofSeconds(30), 3, 500, 1000, Duration.ofSeconds(30)));
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
				Files.setPosixFilePermissions(entry, permissions);
			}
		}
	}

}
