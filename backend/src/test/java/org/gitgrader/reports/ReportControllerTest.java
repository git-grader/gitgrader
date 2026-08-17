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

package org.gitgrader.reports;

import java.util.Locale;
import java.util.UUID;

import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.gitgrader.testsupport.EnabledIfDockerAvailable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.test.context.support.WithMockUser;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.ObjectMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportControllerTest {

	private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-000000000001");

	private static final UUID ASSIGNMENT = UUID.fromString("00000000-0000-0000-0000-000000000002");

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.2-alpine")
		.withDatabaseName("gitgrader")
		.withUsername("gitgrader")
		.withPassword("gitgrader");

	@Autowired
	private ReportController controller;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@DynamicPropertySource
	static void properties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
		registry.add("spring.jpa.properties.hibernate.generate_statistics", () -> "true");
	}

	@BeforeEach
	void seed() {
		this.jdbc.update("DELETE FROM grading_runs");
		this.jdbc.update("DELETE FROM submissions");
		this.jdbc.update("DELETE FROM repositories");
		this.jdbc.update("DELETE FROM assignments");
		this.jdbc.update("DELETE FROM enrollments");
		this.jdbc.update("DELETE FROM students");
		this.jdbc.update("DELETE FROM courses");
		this.jdbc.update("""
				INSERT INTO courses
				(id, course_key, name, timezone, status, registration_enabled, created_at, updated_at)
				VALUES (?, 'bulk-report', 'Bulk report', 'UTC', 'ACTIVE', true,
				        '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z')
				""", COURSE);
		this.jdbc.update("""
				INSERT INTO assignments
				(id, course_id, assignment_key, title, display_order, status, mandatory, timezone,
				 max_points, test_count, pass_threshold, allow_late, network_enabled, created_at, updated_at)
				VALUES (?, ?, 'assignment-one', 'Assignment one', 1, 'DRAFT', true, 'UTC',
				        10.00, 1, 50.00, false, false, '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z')
				""", ASSIGNMENT, COURSE);
		student("00000000-0000-0000-0000-000000000011", "s001", "No", "Submission");
		student("00000000-0000-0000-0000-000000000012", "s002", "Ungraded", "Student");
		student("00000000-0000-0000-0000-000000000013", "s003", "Infrastructure", "Error");
		student("00000000-0000-0000-0000-000000000014", "s004", "Scored", "Student");
		submission("00000000-0000-0000-0000-000000000022", "00000000-0000-0000-0000-000000000012", "RECEIVED",
				"2026-08-02T10:00:00Z");
		submission("00000000-0000-0000-0000-000000000023", "00000000-0000-0000-0000-000000000013",
				"INFRASTRUCTURE_ERROR", "2026-08-02T11:00:00Z");
		submission("00000000-0000-0000-0000-000000000024", "00000000-0000-0000-0000-000000000014", "PASSED",
				"2026-08-02T12:00:00Z");
		this.jdbc.update("""
				INSERT INTO grading_runs
				(id, submission_id, attempt, trigger, status, grading_algorithm_version,
				 tests_total, tests_passed, tests_failed, tests_errored, tests_skipped,
				 score_percent, points_awarded, passed, correlation_id, created_at)
				VALUES ('00000000-0000-0000-0000-000000000034',
				        '00000000-0000-0000-0000-000000000024', 1, 'PUSH', 'COMPLETED', 'v1',
				        4, 3, 1, 0, 0, 75.000, 7.50, true, 'report-test', '2026-08-02T12:01:00Z')
				""");
	}

	@Test
	@DisplayName("keeps the serialized report contract for every assessment state")
	@WithMockUser(roles = "INSTRUCTOR")
	void serializesThePublicContract() throws Exception {
		String json = this.objectMapper.writeValueAsString(this.controller.report(COURSE));

		assertThat(json).isEqualTo(
				"""
						{"courseId":"00000000-0000-0000-0000-000000000001","totalMandatoryAssignments":1,"totalPointsAvailable":10.00,"students":[{"studentId":"00000000-0000-0000-0000-000000000011","studentNumber":"s001","fullName":"No Submission","fullyCompleted":0,"partiallyCompleted":0,"notStarted":1,"completionRate":0.000000,"pointsEarned":0.00,"pointsRate":0.000000,"totalPoints":10.00,"submissionCount":0,"lastActivityAt":null,"assignments":{"assignment-one":{"percent":0,"points":0.00}}},{"studentId":"00000000-0000-0000-0000-000000000012","studentNumber":"s002","fullName":"Ungraded Student","fullyCompleted":0,"partiallyCompleted":0,"notStarted":1,"completionRate":0.000000,"pointsEarned":0.00,"pointsRate":0.000000,"totalPoints":10.00,"submissionCount":0,"lastActivityAt":null,"assignments":{"assignment-one":{"percent":0,"points":0.00}}},{"studentId":"00000000-0000-0000-0000-000000000013","studentNumber":"s003","fullName":"Infrastructure Error","fullyCompleted":0,"partiallyCompleted":0,"notStarted":1,"completionRate":0.000000,"pointsEarned":0.00,"pointsRate":0.000000,"totalPoints":10.00,"submissionCount":0,"lastActivityAt":null,"assignments":{"assignment-one":{"percent":0,"points":0.00}}},{"studentId":"00000000-0000-0000-0000-000000000014","studentNumber":"s004","fullName":"Scored Student","fullyCompleted":1,"partiallyCompleted":0,"notStarted":0,"completionRate":1.000000,"pointsEarned":7.50,"pointsRate":0.750000,"totalPoints":10.00,"submissionCount":1,"lastActivityAt":"2026-08-02T12:00:00Z","assignments":{"assignment-one":{"percent":75.000,"points":7.50}}}]}
						"""
					.strip());
	}

	@Test
	@DisplayName("uses a fixed number of queries regardless of enrolled student count")
	@WithMockUser(roles = "INSTRUCTOR")
	void boundsTheQueryCount() {
		// The report used to ask per student, then per student and assignment, then again
		// per graded submission: about 9,300 queries for a class of 300 with ten
		// assignments, all competing for the same ten connections a push needs. Asserting
		// a constant is only meaningful against two different class sizes, so the count
		// is
		// taken once and then again with the class doubled.
		Statistics statistics = this.entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.clear();
		this.controller.report(COURSE);
		long forFourStudents = statistics.getPrepareStatementCount();

		for (int extra = 0; extra < 4; extra++) {
			student(String.format(Locale.ROOT, "00000000-0000-0000-0000-0000000000%02d", 20 + extra),
					"s1" + (20 + extra), "Extra", "Student" + extra);
		}
		statistics.clear();
		this.controller.report(COURSE);

		assertThat(forFourStudents).isEqualTo(6);
		assertThat(statistics.getPrepareStatementCount()).as("the query count must not follow the class size")
			.isEqualTo(forFourStudents);
	}

	private void student(String id, String number, String firstName, String lastName) {
		this.jdbc.update("""
				INSERT INTO students
				(id, student_number, first_name, last_name, email, status, registered_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ? || '@example.org', 'SELF_REGISTERED',
				        '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z')
				""", UUID.fromString(id), number, firstName, lastName, number);
		this.jdbc.update("""
				INSERT INTO enrollments
				(id, student_id, course_id, status, enrolled_at, created_at, updated_at)
				VALUES (?, ?, ?, 'ACTIVE', '2026-08-01T00:00:00Z',
				        '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z')
				""", UUID.randomUUID(), UUID.fromString(id), COURSE);
	}

	private void submission(String id, String studentId, String status, String receivedAt) {
		UUID repository = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO repositories
				(id, assignment_id, student_id, repository_path, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'READY', '2026-08-01T00:00:00Z', '2026-08-01T00:00:00Z')
				""", repository, ASSIGNMENT, UUID.fromString(studentId), "bulk-report/assignment-one/" + studentId);
		this.jdbc.update("""
				INSERT INTO submissions
				(id, repository_id, student_id, course_id, assignment_id, commit_sha, git_ref,
				 received_at, signature_status, status, late, created_at)
				VALUES (?, ?, ?, ?, ?, ?, 'refs/heads/main', ?::timestamptz,
				        'VERIFIED', ?, false, ?::timestamptz)
				""", UUID.fromString(id), repository, UUID.fromString(studentId), COURSE, ASSIGNMENT,
				id.substring(id.length() - 1).repeat(40), receivedAt, status, receivedAt);
	}

}
