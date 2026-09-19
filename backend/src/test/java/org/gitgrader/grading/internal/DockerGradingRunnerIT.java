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

	private static final String SHIM_CLIENT_SUITE_FOR_THE_PARTIAL_SOLUTION = """
			import assert from 'node:assert/strict';
			import test from 'node:test';
			import { createShimClient } from '/opt/gitgrader-shim/client.js';

			const { proxy } = await createShimClient();

			test('h01 truncate preserves text at the maximum length', async () => {
			  assert.equal(await proxy.truncate('exact', 5), 'exact');
			});

			test('h02 truncate counts Unicode characters rather than UTF-16 units', async () => {
			  assert.equal(await proxy.truncate('😀ab', 2), '😀a');
			});

			test('h03 slugify normalises accented letters', async () => {
			  assert.equal(await proxy.slugify('Crème Brûlée'), 'creme-brulee');
			});

			test('h04 slugify collapses punctuation and whitespace', async () => {
			  assert.equal(await proxy.slugify('Hello,   world!!!'), 'hello-world');
			});

			test('h05 titleCase capitalises hyphenated words', async () => {
			  assert.equal(await proxy.titleCase('the quick-brown FOX'), 'The Quick-Brown Fox');
			});

			test('h06 wordCount returns zero for empty text', async () => {
			  assert.equal(await proxy.wordCount(''), 0);
			});

			test('h07 wordCount accepts mixed whitespace', async () => {
			  assert.equal(await proxy.wordCount('one\\t two\\nthree'), 3);
			});

			test('h08 reverseWords normalises repeated whitespace', async () => {
			  assert.equal(await proxy.reverseWords('  one   two\\tthree  '), 'three two one');
			});

			test('h09 isPalindrome ignores case and punctuation', async () => {
			  assert.equal(await proxy.isPalindrome('A man, a plan, a canal: Panama!'), true);
			});

			test('h10 isPalindrome rejects a non-palindrome', async () => {
			  assert.equal(await proxy.isPalindrome('OpenAI'), false);
			});
			""";

	private static final String HOSTILE_SUBMISSION = """
			import fs from 'node:fs';
			import { readFile } from 'node:fs/promises';

			export async function exfilListHidden() {
			  try {
			    return JSON.stringify(fs.readdirSync('/opt/hidden-tests'));
			  }
			  catch (error) {
			    return `error: ${error.code}`;
			  }
			}

			export async function exfilReadManifest() {
			  try {
			    return await readFile('/opt/hidden-tests/manifest.json', 'utf8');
			  }
			  catch (error) {
			    return `error: ${error.code}`;
			  }
			}

			export async function exfilReachNetwork() {
			  try {
			    const response = await fetch('http://127.0.0.1:80/');
			    return `ok ${response.status}`;
			  }
			  catch (error) {
			    return 'error reaching the network';
			  }
			}
			""";

	private static final String HOSTILE_SUITE = """
			import assert from 'node:assert/strict';
			import test from 'node:test';
			import { createShimClient } from '/opt/gitgrader-shim/client.js';

			const { proxy } = await createShimClient();

			test('h01 the sandbox cannot list the hidden tests', async () => {
			  assert.match(await proxy.exfilListHidden(), /error: ENOENT/);
			});

			test('h02 the sandbox cannot read the hidden manifest', async () => {
			  assert.match(await proxy.exfilReadManifest(), /error: ENOENT/);
			});

			test('h03 the sandbox cannot reach the network', async () => {
			  assert.doesNotMatch(await proxy.exfilReachNetwork(), /^ok /);
			});
			""";

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

	@Test
	@DisplayName("grades over the protocol, the suite and the sandbox split and the same 7 of 10")
	void gradesThePartialSolutionOverTwoContainers(@TempDir Path tempDir) throws IOException, InterruptedException {
		// The two containers of a shimmed run have separate filesystems: one holds the
		// submission, the other the hidden tests. Only the shared Unix socket connects
		// them, and the suite's side is the whole report.
		Path shim = Path.of("..", "deployment", "runtimes", "node-shim").toAbsolutePath().normalize();
		Assumptions.assumeTrue(Files.isDirectory(shim), "node shim runtime is not present");
		GradingProperties properties = properties(shim.toString());
		DockerClient client = new DockerClientConfiguration().dockerClient(properties);
		Assumptions.assumeTrue(Files.isDirectory(EXAMPLE), "example assignment is not present");
		ensureImagePresent(client);
		StorageProperties storage = storageIn(tempDir);

		Path workspace = tempDir.resolve("workspace");
		copyDirectory(EXAMPLE.resolve("template"), workspace);
		Files.copy(EXAMPLE.resolve("reference-solution/partial-70/string-utils.js"),
				workspace.resolve("src/string-utils.js"), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		makeWorldReadable(workspace);

		Path suite = tempDir.resolve("suite");
		Files.createDirectories(suite);
		Files.writeString(suite.resolve("hidden.test.js"), SHIM_CLIENT_SUITE_FOR_THE_PARTIAL_SOLUTION);

		GradingResult result = shimRunner(properties, client, storage).execute(new GradingExecutionRequest(workspace,
				suite, IMAGE, null, "node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js",
				Duration.ofMinutes(3), DataSize.ofMegabytes(512).toBytes(), 1.0, 256, false,
				DataSize.ofMegabytes(1).toBytes(), "corr-it", Map.of(), "node-ipc", null));

		assertThat(result.infrastructureFailure()).as("the run itself must succeed: %s", result.failureDetail())
			.isFalse();
		assertThat(result.timedOut()).isFalse();
		assertThat(result.stdout()).contains("# pass 7").contains("# fail 3").contains("1..10");
		assertThat(result.exitCode()).isNotZero();
	}

	@Test
	@DisplayName("a hostile submission cannot reach the hidden suite or the network")
	void shimmedSandboxCannotReachTheHiddenSuiteOrTheNetwork(@TempDir Path tempDir)
			throws IOException, InterruptedException {
		// The sandbox gets no bind to the hidden tests, no HIDDEN_TESTS variable and no
		// network. Exfiltration attempts fail where they would have succeeded in one
		// container.
		Path shim = Path.of("..", "deployment", "runtimes", "node-shim").toAbsolutePath().normalize();
		Assumptions.assumeTrue(Files.isDirectory(shim), "node shim runtime is not present");
		GradingProperties properties = properties(shim.toString());
		DockerClient client = new DockerClientConfiguration().dockerClient(properties);
		Assumptions.assumeTrue(Files.isDirectory(EXAMPLE), "example assignment is not present");
		ensureImagePresent(client);
		StorageProperties storage = storageIn(tempDir);

		Path workspace = tempDir.resolve("workspace");
		copyDirectory(EXAMPLE.resolve("template"), workspace);
		Files.writeString(workspace.resolve("src/string-utils.js"), HOSTILE_SUBMISSION);
		makeWorldReadable(workspace);

		Path suite = tempDir.resolve("suite");
		Files.createDirectories(suite);
		Files.writeString(suite.resolve("hidden.test.js"), HOSTILE_SUITE);

		GradingResult result = shimRunner(properties, client, storage).execute(new GradingExecutionRequest(workspace,
				suite, IMAGE, null, "node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js",
				Duration.ofMinutes(3), DataSize.ofMegabytes(512).toBytes(), 1.0, 256, false,
				DataSize.ofMegabytes(1).toBytes(), "corr-it", Map.of(), "node-ipc", null));

		assertThat(result.infrastructureFailure()).as("the run itself must succeed: %s", result.failureDetail())
			.isFalse();
		assertThat(result.timedOut()).isFalse();
		assertThat(result.stdout()).contains("1..3").contains("# pass 3").contains("# fail 0");
	}

	private static StorageProperties storageIn(Path tempDir) throws IOException {
		// The socket directory is created under the storage temp root, which must exist
		// for real when the daemon is real.
		Path tmp = tempDir.resolve("tmp");
		Files.createDirectories(tmp);
		return new StorageProperties(tempDir.resolve("repositories").toString(),
				tempDir.resolve("templates").toString(), tempDir.resolve("tests").toString(),
				tempDir.resolve("artifacts").toString(), tmp.toString());
	}

	private static DockerGradingRunner shimRunner(GradingProperties properties, DockerClient client,
			StorageProperties storage) {
		return new DockerGradingRunner(client, properties, Clock.systemUTC(), storage, (image) -> Optional.empty());
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
		return properties("");
	}

	private static GradingProperties properties(String shimMountPath) {
		return new GradingProperties("docker", 2, Duration.ofSeconds(120), DataSize.ofMegabytes(512), 1.0, 256, false,
				DataSize.ofMegabytes(1), false,
				new GradingProperties.Docker("unix:///var/run/docker.sock", "", "", "65534:65534",
						Duration.ofMinutes(5), true, DataSize.ofMegabytes(64), true, true, shimMountPath),
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
