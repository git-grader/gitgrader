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

package org.gitgrader.git;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.gitgrader.assignments.AssignmentAdministration;
import org.gitgrader.assignments.AssignmentDefinition;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseAdministration;
import org.gitgrader.courses.CourseDefinition;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.identity.StudentRegistration;
import org.gitgrader.identity.StudentRegistry;
import org.gitgrader.identity.StudentView;
import org.gitgrader.runtimes.RuntimeAdministration;
import org.gitgrader.runtimes.RuntimeDefinition;
import org.gitgrader.runtimes.RuntimeView;
import org.gitgrader.sshkeys.SshKeyOrigin;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.templates.TemplateAdministration;
import org.gitgrader.templates.TemplateVersionView;
import org.gitgrader.templates.TestSuiteAdministration;
import org.gitgrader.templates.TestSuiteVersionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assumptions.assumeThat;

/**
 * End-to-end proof that the Git SSH endpoint accepts real pushes.
 *
 * <p>
 * <strong>Why this test drives the real {@code git} binary.</strong> Everything in this
 * flow is a protocol interaction: OpenSSH picks the key, git speaks the pack protocol,
 * and the server answers on the side band. A test written against JGit's in-process
 * transport would exercise none of that and would keep passing if the SSH server were
 * misconfigured. Driving the same client a student uses is the only way to know the
 * endpoint actually works.
 *
 * <p>
 * Covers the acceptance criteria the brief calls out by name: a student clones an
 * unlocked assignment, an unsigned commit is refused with an actionable message, a signed
 * commit is accepted, the signature reports as {@code Verified}, and the push output
 * carries a result URL.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Testcontainers
class GitPushOverSshIT {

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.2-alpine")
		.withDatabaseName("gitgrader")
		.withUsername("gitgrader")
		.withPassword("gitgrader");

	/** Fixed high port for the SSH endpoint under test. */
	private static final int SSH_PORT = 22_345;

	private static final String STUDENT_NUMBER = "s1000042";

	@TempDir
	private Path scratch;

	@Autowired
	private CourseAdministration courses;

	@Autowired
	private AssignmentAdministration assignments;

	@Autowired
	private RuntimeAdministration runtimes;

	@Autowired
	private TemplateAdministration templates;

	@Autowired
	private TestSuiteAdministration testSuites;

	@Autowired
	private StudentRegistry students;

	@Autowired
	private SshKeyRegistry sshKeys;

	@Autowired
	private SubmissionService submissions;

	@Autowired
	private org.gitgrader.git.internal.GitRepositoryService repositoryService;

	@Autowired
	private org.gitgrader.configuration.StorageProperties storage;

	@Autowired
	private org.gitgrader.grading.internal.GradingRunRepository gradingRuns;

	private Path keyFile;

	private String repositoryPath;

	private UUID studentId;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("git.enabled", () -> "true");
		registry.add("git.listen-port", () -> SSH_PORT);
		registry.add("git.ssh-port", () -> SSH_PORT);
		registry.add("git.require-signed-commits", () -> "true");
	}

	@BeforeEach
	void seed() throws Exception {
		assumeThat(commandAvailable("git")).as("git CLI available").isTrue();
		assumeThat(commandAvailable("ssh-keygen")).as("ssh-keygen available").isTrue();

		// The database is fresh on every run (a new container), but the data directory
		// under target/ survives. Leaving it would make the two disagree: the database
		// would have no repository while the bare directory still existed on disk.
		clearDataDirectory();

		this.keyFile = this.scratch.resolve("id_ed25519");
		run(this.scratch, "ssh-keygen", "-t", "ed25519", "-N", "", "-C", "student@example.org", "-f",
				this.keyFile.toString(), "-q");
		String publicKey = Files.readString(this.scratch.resolve("id_ed25519.pub"), StandardCharsets.UTF_8);

		StudentView student = this.students.register(
				new StudentRegistration(STUDENT_NUMBER, "Max", "Muster", "max.muster@example.org", "CLASS-A", null));
		this.studentId = student.id();
		this.sshKeys.register(student.id(), "test key", publicKey, SshKeyOrigin.REGISTRATION, null);

		AssignmentView assignment = seedAssignment();
		this.repositoryPath = org.gitgrader.git.internal.GitRepositoryService.repositoryPathFor("course-e2e",
				"assignment-01", STUDENT_NUMBER);
		this.repositoryService.provision(assignment.id(), student.id(), this.repositoryPath,
				assignment.templateVersionId());
	}

	@Test
	@DisplayName("clones over SSH, refuses an unsigned commit, then accepts a signed one")
	void acceptsSignedPushAndRefusesUnsigned() throws Exception {
		Path clone = this.scratch.resolve("work");

		Result cloned = run(this.scratch, gitEnv(), "git", "clone", cloneUrl(), clone.toString());
		assertThat(cloned.exitCode()).as("clone output:\n%s", cloned.output()).isZero();

		assertThat(clone.resolve("README.md")).as("template content must reach the student").exists();
		assertThat(clone.resolve("src").resolve("solution.js")).exists();

		configureIdentity(clone);
		writeSolution(clone, "export const answer = 41;");

		// UNSIGNED: must be refused, and the refusal must tell the student what to do.
		run(clone, "git", "add", ".");
		run(clone, "git", "-c", "commit.gpgsign=false", "commit", "-m", "unsigned attempt");
		Result unsigned = run(clone, gitEnv(), "git", "push", "origin", "HEAD:main");

		assertThat(unsigned.exitCode()).as("an unsigned push must fail").isNotZero();
		assertThat(unsigned.output()).contains("is not signed");
		assertThat(unsigned.output()).contains("commit.gpgsign true");
		assertThat(this.submissions.findLatestForStudent(this.studentId))
			.as("a refused push must not create a submission")
			.isEmpty();

		// SIGNED: same content, now signed with the registered key.
		writeSolution(clone, "export const answer = 42;");
		run(clone, "git", "add", ".");
		run(clone, signingEnv(), "git", "commit", "--amend", "-S", "-m", "signed solution");
		Result signed = run(clone, gitEnv(), "git", "push", "origin", "HEAD:main");

		assertThat(signed.exitCode()).as("signed push output:\n%s", signed.output()).isZero();
		assertThat(signed.output()).contains("Signature: Verified");
		assertThat(signed.output()).contains("Submission accepted.");
		assertThat(signed.output()).contains("/result/");

		SubmissionView recorded = this.submissions.findLatestForStudent(this.studentId).orElseThrow();
		assertThat(recorded.signatureVerified()).isTrue();
		assertThat(recorded.late()).isFalse();

		// The push must also have queued grading work. The listener runs after the
		// publishing transaction commits, so this is polled rather than asserted inline.
		org.awaitility.Awaitility.await()
			.atMost(java.time.Duration.ofSeconds(20))
			.untilAsserted(() -> assertThat(this.gradingRuns.findFirstBySubmissionIdOrderByAttemptDesc(recorded.id()))
				.as("an accepted push must queue a grading run")
				.isPresent());
	}

	private void clearDataDirectory() throws IOException {
		for (Path root : List.of(this.storage.repositories(), this.storage.templates(), this.storage.tests())) {
			if (!Files.exists(root)) {
				continue;
			}
			try (java.util.stream.Stream<Path> paths = Files.walk(root)) {
				for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
					Files.deleteIfExists(path);
				}
			}
		}
	}

	private AssignmentView seedAssignment() throws IOException {
		CourseView course = this.courses
			.createCourse(new CourseDefinition("course-e2e", "End to end course", null, "2026FS",
					LocalDate.now(java.time.Clock.systemUTC()), null, "UTC", CourseStatus.ACTIVE, null, null, true));
		RuntimeView runtime = this.runtimes.create(new RuntimeDefinition("node-24-e2e", "Node.js 24",
				"registry.example.org/gitgrader/runtime-node", "24.13.0", "sha256:" + "a".repeat(64), "npm ci",
				"npm test", org.gitgrader.runtimes.ReportFormat.TAP, true));

		writeTemplateContent();
		UUID templateId = this.templates.createTemplate("tpl-e2e", "Template", null);
		TemplateVersionView templateVersion = this.templates
			.publish(this.templates.createVersion(templateId, "1.0.0", "tpl-e2e/1.0.0").id(), "test");
		UUID suiteId = this.testSuites.createTestSuite("suite-e2e", "Suite", null);
		TestSuiteVersionView suiteVersion = this.testSuites
			.publish(this.testSuites.createVersion(suiteId, "1.0.0", "suite-e2e/1.0.0").id(), "test", 10, 4);

		Instant now = Instant.now(java.time.Clock.systemUTC());
		return this.assignments.create(new AssignmentDefinition(course.id(), "assignment-01", "Assignment 01", null, 1,
				AssignmentStatus.OPEN, true, now.minus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS), "UTC",
				new BigDecimal("100"), 10, new BigDecimal("100"), false, templateVersion.id(), suiteVersion.id(),
				runtime.id(), null, null, null, null, false));
	}

	/**
	 * Materialises the on-disk content that publication validates.
	 *
	 * <p>
	 * Publishing scans the tree and refuses anything that looks like hidden test
	 * material, so the template gets only student-visible files and the hidden suite is
	 * written to a completely separate root.
	 * @throws IOException if the fixture cannot be written
	 */
	private void writeTemplateContent() throws IOException {
		Path template = this.storage.templates().resolve("tpl-e2e").resolve("1.0.0");
		Files.createDirectories(template.resolve("src"));
		Files.writeString(template.resolve("README.md"), "# Assignment 01\n", StandardCharsets.UTF_8);
		Files.writeString(template.resolve("src").resolve("solution.js"), "export const answer = 0;\n",
				StandardCharsets.UTF_8);

		Path suite = this.storage.tests().resolve("suite-e2e").resolve("1.0.0");
		Files.createDirectories(suite);
		Files.writeString(suite.resolve("hidden.test.js"), "// hidden\n", StandardCharsets.UTF_8);
	}

	private String cloneUrl() {
		return "ssh://git@127.0.0.1:" + SSH_PORT + "/" + this.repositoryPath + ".git";
	}

	private List<String> gitEnv() {
		return List.of("GIT_SSH_COMMAND=ssh -i " + this.keyFile + " -o StrictHostKeyChecking=no"
				+ " -o UserKnownHostsFile=/dev/null -o IdentitiesOnly=yes -o LogLevel=ERROR");
	}

	private List<String> signingEnv() {
		List<String> env = new ArrayList<>(gitEnv());
		env.add("GIT_CONFIG_COUNT=3");
		env.add("GIT_CONFIG_KEY_0=gpg.format");
		env.add("GIT_CONFIG_VALUE_0=ssh");
		env.add("GIT_CONFIG_KEY_1=user.signingkey");
		env.add("GIT_CONFIG_VALUE_1=" + this.keyFile);
		env.add("GIT_CONFIG_KEY_2=commit.gpgsign");
		env.add("GIT_CONFIG_VALUE_2=true");
		return env;
	}

	private void configureIdentity(Path clone) throws Exception {
		run(clone, "git", "config", "user.email", "max.muster@example.org");
		run(clone, "git", "config", "user.name", "Max Muster");
	}

	private void writeSolution(Path clone, String content) throws IOException {
		Files.writeString(clone.resolve("solution.js"), content, StandardCharsets.UTF_8);
	}

	private static boolean commandAvailable(String command) {
		try {
			return new ProcessBuilder(command, "--version").start().waitFor(10, TimeUnit.SECONDS);
		}
		catch (IOException | InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
	}

	private Result run(Path workingDirectory, String... command) throws Exception {
		return run(workingDirectory, List.of(), command);
	}

	private Result run(Path workingDirectory, List<String> environment, String... command) throws Exception {
		ProcessBuilder builder = new ProcessBuilder(command).directory(workingDirectory.toFile())
			.redirectErrorStream(true);
		environment.forEach((entry) -> {
			int split = entry.indexOf('=');
			builder.environment().put(entry.substring(0, split), entry.substring(split + 1));
		});
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		boolean finished = process.waitFor(90, TimeUnit.SECONDS);
		if (!finished) {
			process.destroyForcibly();
			throw new IllegalStateException("Command timed out: " + String.join(" ", command));
		}
		return new Result(process.exitValue(), output);
	}

	/**
	 * The outcome of one external command.
	 *
	 * @param exitCode the process exit code
	 * @param output combined stdout and stderr
	 */
	private record Result(int exitCode, String output) {
	}

}
