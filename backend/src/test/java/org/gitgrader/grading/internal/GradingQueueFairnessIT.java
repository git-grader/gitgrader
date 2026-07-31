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
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentAdministration;
import org.gitgrader.assignments.AssignmentDefinition;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.courses.CourseAdministration;
import org.gitgrader.courses.CourseDefinition;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.git.internal.GitRepositoryService;
import org.gitgrader.grading.GradingJobStatus;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.domain.GradingJob;
import org.gitgrader.identity.StudentRegistration;
import org.gitgrader.identity.StudentRegistry;
import org.gitgrader.identity.StudentView;
import org.gitgrader.runtimes.RuntimeAdministration;
import org.gitgrader.runtimes.RuntimeDefinition;
import org.gitgrader.runtimes.RuntimeView;
import org.gitgrader.submissions.NewSubmission;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionRefusedException;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.templates.TemplateAdministration;
import org.gitgrader.templates.TemplateVersionView;
import org.gitgrader.templates.TestSuiteAdministration;
import org.gitgrader.templates.TestSuiteVersionView;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.awaitility.Awaitility.await;

/**
 * Proves the queue bounds what one student can occupy, against a real PostgreSQL server.
 *
 * <p>
 * These guarantees are all enforced by SQL - a partial unique index, a
 * {@code DISTINCT ON} head selection and a {@code FOR UPDATE SKIP LOCKED} anti-join - so
 * a test with a mocked repository would assert only that the Java around them compiles.
 * The behaviour under test is the database's.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
class GradingQueueFairnessIT {

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.2-alpine")
		.withDatabaseName("gitgrader")
		.withUsername("gitgrader")
		.withPassword("gitgrader");

	private static final Clock CLOCK = Clock.systemUTC();

	private static final String SHUTDOWN_WORKER = "worker-shutdown-gate";

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
	private SubmissionService submissions;

	@Autowired
	private GitRepositoryService repositoryService;

	@Autowired
	private StorageProperties storage;

	@Autowired
	private GradingJobRepository jobs;

	@Autowired
	private GradingRunRepository runs;

	@Autowired
	private GradingQueue queue;

	@Autowired
	private GradingDispatcher dispatcher;

	@Autowired
	private JdbcTemplate jdbc;

	private CourseView course;

	private AssignmentView first;

	private AssignmentView second;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		// The test profile claims one job at a time, which would hide the per-student
		// selection behind the batch size: any batch of one trivially holds one student.
		registry.add("grading.max-parallel-jobs", () -> "10");
	}

	@BeforeEach
	void seed() throws IOException {
		// The dispatcher polls every two seconds in this context and would claim the very
		// jobs these tests are about to claim, so any assertion about a claim would race
		// it. Stopping it leaves this class the only claimant; the one test that cares
		// about the lifecycle starts it again itself.
		this.dispatcher.stop();

		// The queue is global and every test here claims from it, so work left runnable
		// by the previous test would be swept into this one's batch. Retiring it with a
		// native update rather than deleting avoids racing the optimistic locking on rows
		// the previous test may still hold.
		this.jdbc.update("UPDATE grading_jobs SET status = 'DONE', finished_at = now(), updated_at = now() "
				+ "WHERE status IN ('PENDING', 'CLAIMED', 'RUNNING')");

		this.course = this.courses.createCourse(new CourseDefinition(unique("course"), "Queue course", null, "2026FS",
				LocalDate.now(CLOCK), null, "UTC", CourseStatus.ACTIVE, null, null, true));
		this.first = createAssignment("assignment-one");
		this.second = createAssignment("assignment-two");
	}

	@Test
	@DisplayName("a newer push supersedes the queued run the previous one created")
	void newerPushSupersedesQueuedWork() {
		Student student = enrol("s100");
		SubmissionView older = push(student, this.first, sha('a'));
		SubmissionView newer = push(student, this.first, sha('b'));

		GradingJob supersededJob = jobFor(older);
		assertThat(supersededJob.status()).isEqualTo(GradingJobStatus.CANCELLED);
		assertThat(this.runs.findById(supersededJob.gradingRunId()).orElseThrow().status())
			.isEqualTo(GradingRunStatus.CANCELLED);
		assertThat(this.submissions.findById(older.id()).orElseThrow().status()).isEqualTo(SubmissionStatus.CANCELLED);

		assertThat(jobFor(newer).status()).isEqualTo(GradingJobStatus.PENDING);
		assertThat(this.jobs.countByStudentIdAndCourseIdAndStatus(student.id(), this.course.id(),
				GradingJobStatus.PENDING))
			.isOne();
	}

	@Test
	@DisplayName("work already claimed by a worker is never superseded")
	void claimedWorkSurvivesANewerPush() {
		Student student = enrol("s101");
		SubmissionView running = push(student, this.first, sha('a'));
		assertThat(this.queue.claimBatch("worker-test")).containsExactly(jobFor(running).id());

		SubmissionView newer = push(student, this.first, sha('b'));

		assertThat(jobFor(running).status()).isEqualTo(GradingJobStatus.CLAIMED);
		assertThat(this.submissions.findById(running.id()).orElseThrow().status())
			.isNotEqualTo(SubmissionStatus.CANCELLED);
		assertThat(jobFor(newer).status()).isEqualTo(GradingJobStatus.PENDING);
	}

	@Test
	@DisplayName("claims at most one job per student however many assignments they queued")
	void claimsOneJobPerStudent() {
		Student busy = enrol("s102");
		Student other = enrol("s103");
		push(busy, this.first, sha('a'));
		push(busy, this.second, sha('b'));
		push(other, this.first, sha('c'));

		List<UUID> claimed = this.queue.claimBatch("worker-test");

		assertThat(studentsAmong(claimed)).containsOnlyOnce(busy.id());
		assertThat(studentsAmong(claimed)).containsOnlyOnce(other.id());
	}

	@Test
	@DisplayName("a student already occupying a worker is skipped by the next claim")
	void studentWithRunningWorkIsSkipped() {
		Student student = enrol("s104");
		push(student, this.first, sha('a'));
		push(student, this.second, sha('b'));

		assertThat(studentsAmong(this.queue.claimBatch("worker-test"))).containsOnlyOnce(student.id());
		assertThat(studentsAmong(this.queue.claimBatch("worker-test"))).doesNotContain(student.id());
	}

	/**
	 * Maps claimed job ids to the students they belong to.
	 *
	 * <p>
	 * The queue is shared with every other test in this class, so an assertion on the
	 * whole batch would depend on execution order. Only the students a test enrolled are
	 * ever asserted on.
	 * @param claimed the ids one claim returned
	 * @return the owning student of each
	 */
	private List<UUID> studentsAmong(List<UUID> claimed) {
		return claimed.stream().map(this::job).map(GradingJob::studentId).toList();
	}

	@Test
	@DisplayName("returning a held job to the queue refunds the attempt it consumed")
	void shutdownRequeueRefundsTheAttempt() {
		Student student = enrol("s105");
		SubmissionView submission = push(student, this.first, sha('a'));
		this.queue.claimBatch("worker-shutdown");
		assertThat(jobFor(submission).attempts()).isOne();

		// Asserted on this job rather than on the returned count: sibling tests share the
		// queue, so the number of jobs a worker happens to hold is not this test's to
		// fix.
		this.queue.requeueHeldJobs("worker-shutdown");

		GradingJob returned = jobFor(submission);
		assertThat(returned.status()).isEqualTo(GradingJobStatus.PENDING);
		assertThat(returned.attempts()).isZero();
		assertThat(this.runs.findById(returned.gradingRunId()).orElseThrow().status())
			.isEqualTo(GradingRunStatus.QUEUED);
	}

	@Test
	@DisplayName("the same commit cannot be submitted twice")
	void refusesADuplicateCommit() {
		Student student = enrol("s106");
		push(student, this.first, sha('a'));

		assertThatExceptionOfType(SubmissionRefusedException.class)
			.isThrownBy(() -> push(student, this.first, sha('a')))
			.matches((ex) -> ex.reason() == SubmissionRefusedException.Reason.DUPLICATE_COMMIT);
	}

	@Test
	@DisplayName("a student cannot exceed the rolling hourly allowance for one assignment")
	void refusesBeyondTheHourlyAssignmentAllowance() {
		Student student = enrol("s107");
		for (int i = 0; i < 20; i++) {
			record(student, this.first, String.format("%040x", i));
		}

		assertThatExceptionOfType(SubmissionRefusedException.class)
			.isThrownBy(() -> record(student, this.first, sha('f')))
			.matches((ex) -> ex.reason() == SubmissionRefusedException.Reason.ASSIGNMENT_RATE_LIMIT);
	}

	@Test
	@DisplayName("a stopping dispatcher accepts no new work and hands back what it holds")
	void shutdownStopsAcceptingAndReturnsHeldWork() {
		Student student = enrol("s108");
		SubmissionView submission = push(student, this.first, sha('a'));
		this.queue.claimBatch(SHUTDOWN_WORKER);
		assertThat(jobFor(submission).status()).isEqualTo(GradingJobStatus.CLAIMED);

		this.dispatcher.start();
		assertThat(this.dispatcher.isRunning()).isTrue();

		this.dispatcher.stop();

		assertThat(this.dispatcher.isRunning()).isFalse();
		this.dispatcher.poll();
		assertThat(this.jobs.findByClaimedByAndStatusIn(this.dispatcher.workerId(),
				List.of(GradingJobStatus.CLAIMED, GradingJobStatus.RUNNING)))
			.as("a stopped dispatcher must claim nothing")
			.isEmpty();

		assertThat(this.queue.requeueHeldJobs(SHUTDOWN_WORKER)).isPositive();
		assertThat(jobFor(submission).status()).isEqualTo(GradingJobStatus.PENDING);
	}

	private GradingJob jobFor(SubmissionView submission) {
		return findJob(submission)
			.orElseThrow(() -> new AssertionError("No grading job for submission " + submission.id()));
	}

	private Optional<GradingJob> findJob(SubmissionView submission) {
		return this.jobs.findAll().stream().filter((job) -> job.submissionId().equals(submission.id())).findFirst();
	}

	private GradingJob job(UUID jobId) {
		return this.jobs.findById(jobId).orElseThrow();
	}

	/**
	 * Records a push and waits for the queue to catch up with it.
	 *
	 * <p>
	 * Queueing is driven by {@code SubmissionRecorded}, which Spring Modulith delivers
	 * asynchronously once the recording transaction commits. Asserting straight after
	 * {@code record} would race that listener, so every test that inspects a job observes
	 * the queue only once that job exists. Tests that only need the submission to have
	 * happened call {@link #record} instead and skip the wait.
	 * @param student the pushing student
	 * @param assignment the assignment being answered
	 * @param commitSha the commit at the tip
	 * @return the recorded submission
	 */
	private SubmissionView push(Student student, AssignmentView assignment, String commitSha) {
		SubmissionView recorded = record(student, assignment, commitSha);
		await().atMost(Duration.ofSeconds(30)).until(() -> findJob(recorded).isPresent());
		return recorded;
	}

	private SubmissionView record(Student student, AssignmentView assignment, String commitSha) {
		return this.submissions.record(NewSubmission.builder()
			.target(student.repositoryFor(assignment), student.id(), this.course.id(), assignment.id())
			.repositoryPath("path/" + assignment.assignmentKey() + "/" + student.number())
			.commit(commitSha, "refs/heads/main", "work", Instant.now(CLOCK))
			.receivedAt(Instant.now(CLOCK))
			.signature(SignatureVerdict.VERIFIED, null, null, null)
			.pins(assignment.templateVersionId(), assignment.testSuiteVersionId(), assignment.runtimeId(), null)
			.admission(SubmissionStatus.RECEIVED, false, null)
			.build());
	}

	private Student enrol(String number) {
		StudentView view = this.students
			.register(new StudentRegistration(number, "Given", "Family", number + "@example.org", "CLASS-A", null));
		UUID firstRepository = provision(this.first, view, number);
		UUID secondRepository = provision(this.second, view, number);
		return new Student(view.id(), number, this.first.id(), firstRepository, secondRepository);
	}

	private UUID provision(AssignmentView assignment, StudentView student, String number) {
		String path = GitRepositoryService.repositoryPathFor(this.course.courseKey(), assignment.assignmentKey(),
				number);
		return this.repositoryService.provision(assignment.id(), student.id(), path, assignment.templateVersionId())
			.id();
	}

	private AssignmentView createAssignment(String key) throws IOException {
		String templateKey = unique("tpl");
		String suiteKey = unique("suite");
		RuntimeView runtime = this.runtimes.create(new RuntimeDefinition(unique("rt"), "Node.js 24",
				"registry.example.org/gitgrader/runtime-node", "24.13.0", "sha256:" + "a".repeat(64), "npm ci",
				"npm test", org.gitgrader.runtimes.ReportFormat.TAP, true));
		writeContent(templateKey, suiteKey);

		UUID templateId = this.templates.createTemplate(templateKey, "Template", null);
		TemplateVersionView templateVersion = this.templates
			.publish(this.templates.createVersion(templateId, "1.0.0", templateKey + "/1.0.0").id(), "test");
		UUID suiteId = this.testSuites.createTestSuite(suiteKey, "Suite", null);
		TestSuiteVersionView suiteVersion = this.testSuites
			.publish(this.testSuites.createVersion(suiteId, "1.0.0", suiteKey + "/1.0.0").id(), "test", 10, 4);

		Instant now = Instant.now(CLOCK);
		return this.assignments.create(new AssignmentDefinition(this.course.id(), key, "Assignment", null, 1,
				AssignmentStatus.OPEN, true, now.minus(1, ChronoUnit.DAYS), now.plus(7, ChronoUnit.DAYS), "UTC",
				new BigDecimal("100"), 10, new BigDecimal("100"), false, templateVersion.id(), suiteVersion.id(),
				runtime.id(), null, null, null, null, false));
	}

	private void writeContent(String templateKey, String suiteKey) throws IOException {
		Path template = this.storage.templates().resolve(templateKey).resolve("1.0.0");
		Files.createDirectories(template);
		Files.writeString(template.resolve("README.md"), "# Assignment\n", StandardCharsets.UTF_8);

		Path suite = this.storage.tests().resolve(suiteKey).resolve("1.0.0");
		Files.createDirectories(suite);
		Files.writeString(suite.resolve("hidden.test.js"), "// hidden\n", StandardCharsets.UTF_8);
	}

	private static String sha(char fill) {
		return String.valueOf(fill).repeat(40);
	}

	private static String unique(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
	}

	/** A seeded student together with the repositories provisioned for them. */
	private record Student(UUID id, String number, UUID firstAssignmentId, UUID firstRepository,
			UUID secondRepository) {

		UUID repositoryFor(AssignmentView assignment) {
			return assignment.id().equals(this.firstAssignmentId) ? this.firstRepository : this.secondRepository;
		}
	}

}
