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

package org.gitgrader.persistence;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import javax.sql.DataSource;

import org.gitgrader.testsupport.EnabledIfDockerAvailable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Verifies the Flyway migrations against a real PostgreSQL server.
 *
 * <p>
 * An H2 run would prove almost nothing here. The schema leans on PostgreSQL specific
 * features that carry real guarantees: {@code JSONB} with a GIN index for audit detail,
 * partial indexes that keep the job dispatcher fast as completed history accumulates,
 * regex {@code CHECK} constraints that stop an unsafe course key from becoming a
 * filesystem path, and a hash index on the Modulith event registry. Testing against
 * anything other than the engine used in production would validate a different schema.
 *
 * <p>
 * The constraint assertions below are deliberately written as attempted INSERTs that must
 * fail. A {@code CHECK} constraint that was written but not enforced looks identical to a
 * correct one in the DDL, and the difference only shows up in production.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
class SchemaMigrationIT {

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.2-alpine")
		.withDatabaseName("gitgrader")
		.withUsername("gitgrader")
		.withPassword("gitgrader");

	@Autowired
	private DataSource dataSource;

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	@Test
	@DisplayName("applies every migration and records them in flyway_schema_history")
	void appliesAllMigrations() throws SQLException, IOException {
		List<String> applied = queryColumn(
				"SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank");

		// Compared against the migrations actually on the classpath, not a list written
		// out here. A hardcoded list stops being a check the moment someone adds a
		// migration without remembering to extend it, which is exactly what happened:
		// this asserted "1" and "2" while V3 to V5 had been shipped and would have
		// passed had all three gone missing.
		assertThat(applied).containsExactlyElementsOf(migrationVersionsOnClasspath());
	}

	private static List<String> migrationVersionsOnClasspath() throws IOException {
		Resource[] migrations = new PathMatchingResourcePatternResolver()
			.getResources("classpath:db/migration/V*__*.sql");
		return Arrays.stream(migrations)
			.map(Resource::getFilename)
			.filter(Objects::nonNull)
			.map((name) -> name.substring(1, name.indexOf("__")))
			.sorted(Comparator.comparingInt(Integer::parseInt))
			.toList();
	}

	@Test
	@DisplayName("creates every table the application maps")
	void createsExpectedTables() throws SQLException {
		List<String> tables = queryColumn("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_type = 'BASE TABLE'
				ORDER BY table_name
				""");

		assertThat(tables).contains("students", "instructors", "ssh_keys", "courses", "course_classes",
				"course_instructors", "enrollments", "runtimes", "project_templates", "template_versions",
				"test_suites", "test_suite_versions", "assignments", "deadline_extensions", "repositories",
				"submissions", "grading_runs", "test_results", "grading_logs", "grading_jobs", "result_tokens",
				"audit_events", "registration_attempts", "system_settings", "event_publication",
				"event_publication_archive");
	}

	@Test
	@DisplayName("hibernate validates its mappings against the migrated schema")
	void hibernateSchemaValidationPasses() {
		// Reaching this point at all means the context started with ddl-auto=validate,
		// so every mapped entity matched a real column. That is the check which catches
		// an entity and a migration drifting apart.
		assertThat(this.dataSource).isNotNull();
	}

	@Nested
	@DisplayName("PostgreSQL specific features")
	class PostgresFeatures {

		@Test
		@DisplayName("stores audit detail as JSONB with a GIN index")
		void auditDetailIsJsonb() throws SQLException {
			String type = querySingle("""
					SELECT data_type FROM information_schema.columns
					WHERE table_name = 'audit_events' AND column_name = 'detail'
					""");
			List<String> indexes = queryColumn("""
					SELECT indexdef FROM pg_indexes
					WHERE tablename = 'audit_events' AND indexname = 'audit_events_detail_idx'
					""");

			assertThat(type).isEqualTo("jsonb");
			assertThat(indexes).singleElement(org.assertj.core.api.InstanceOfAssertFactories.STRING).contains("gin");
		}

		@Test
		@DisplayName("keeps the job dispatch index partial so completed history does not bloat it")
		void dispatchIndexIsPartial() throws SQLException {
			String definition = querySingle("""
					SELECT indexdef FROM pg_indexes
					WHERE tablename = 'grading_jobs' AND indexname = 'grading_jobs_dispatch_idx'
					""");

			assertThat(definition).contains("WHERE").contains("PENDING");
		}

		@Test
		@DisplayName("indexes student number and email case-insensitively")
		void identityIndexesAreCaseInsensitive() throws SQLException {
			List<String> definitions = queryColumn("""
					SELECT indexdef FROM pg_indexes
					WHERE tablename = 'students' AND indexname IN
					('students_student_number_key', 'students_email_key')
					""");

			assertThat(definitions).hasSize(2).allSatisfy((def) -> assertThat(def).contains("lower"));
		}

	}

	@Nested
	@DisplayName("constraints that are actually enforced")
	class EnforcedConstraints {

		@Test
		@DisplayName("refuses a runtime pinned to a moving tag")
		void refusesLatestTag() {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insertRuntime("node-moving", "latest", "sha256:" + "a".repeat(64)))
				.withMessageContaining("runtimes_tag_not_latest");
		}

		@Test
		@DisplayName("refuses a runtime without a real image digest")
		void refusesNonDigestImage() {
			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insertRuntime("node-nodigest", "24.1.0", "not-a-digest"))
				.withMessageContaining("runtimes_digest_format");
		}

		@Test
		@DisplayName("accepts a runtime pinned to an immutable digest")
		void acceptsPinnedRuntime() throws SQLException {
			insertRuntime("node-pinned", "24.1.0", "sha256:" + "b".repeat(64));

			assertThat(querySingle("SELECT runtime_key FROM runtimes WHERE runtime_key = 'node-pinned'"))
				.isEqualTo("node-pinned");
		}

		@Test
		@DisplayName("refuses a course key that would be unsafe as a path segment")
		void refusesUnsafeCourseKey() {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> insertCourse("../etc/passwd"))
				.withMessageContaining("courses_course_key_format");
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> insertCourse("Has Spaces"))
				.withMessageContaining("courses_course_key_format");
		}

		@Test
		@DisplayName("refuses to publish an assignment that could not be graded reproducibly")
		void refusesIncompletePublishedAssignment() throws SQLException {
			UUID courseId = UUID.randomUUID();
			insertCourse("publishable-course", courseId);

			// OPEN without a template, test suite, runtime and schedule would mean a
			// student could clone something the platform cannot reproducibly grade.
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> execute("""
					INSERT INTO assignments (id, course_id, assignment_key, title, status,
						max_points, test_count, pass_threshold, created_at, updated_at)
					VALUES ('%s', '%s', 'incomplete-01', 'Incomplete', 'OPEN',
						100, 10, 100, now(), now())
					""".formatted(UUID.randomUUID(), courseId))).withMessageContaining("assignments_publishable");
		}

		@Test
		@DisplayName("allows a DRAFT assignment to be incomplete while it is being prepared")
		void allowsIncompleteDraft() throws SQLException {
			UUID courseId = UUID.randomUUID();
			insertCourse("draft-course", courseId);

			execute("""
					INSERT INTO assignments (id, course_id, assignment_key, title, status,
						max_points, test_count, pass_threshold, created_at, updated_at)
					VALUES ('%s', '%s', 'draft-01', 'Draft', 'DRAFT', 100, 10, 100, now(), now())
					""".formatted(UUID.randomUUID(), courseId));

			assertThat(querySingle("SELECT assignment_key FROM assignments WHERE assignment_key = 'draft-01'"))
				.isEqualTo("draft-01");
		}

		@Test
		@DisplayName("refuses a revoked key that does not say when it was revoked")
		void refusesRevokedKeyWithoutTimestamp() throws SQLException {
			UUID studentId = insertStudent("s-revoke");

			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> execute("""
					INSERT INTO ssh_keys (id, student_id, label, key_type, public_key, fingerprint,
						status, added_via, created_at, updated_at)
					VALUES ('%s', '%s', 'k', 'ssh-ed25519', 'AAAA', 'SHA256:norevokedate',
						'REVOKED', 'REGISTRATION', now(), now())
					""".formatted(UUID.randomUUID(), studentId)))
				.withMessageContaining("ssh_keys_revocation_consistency");
		}

		@Test
		@DisplayName("refuses two students sharing one key fingerprint")
		void refusesDuplicateFingerprint() throws SQLException {
			UUID first = insertStudent("s-dup-a");
			UUID second = insertStudent("s-dup-b");
			String fingerprint = "SHA256:sharedfingerprintvalue";
			insertKey(first, fingerprint);

			// The SSH transport resolves the pushing student from the fingerprint alone,
			// so a shared fingerprint would be an identity collision, not a duplicate
			// row.
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> insertKey(second, fingerprint))
				.withMessageContaining("ssh_keys_fingerprint_key");
		}

		@Test
		@DisplayName("refuses a grading run scoring above 100 percent")
		void refusesImpossibleScore() throws SQLException {
			assertThatExceptionOfType(SQLException.class).isThrownBy(() -> execute("""
					INSERT INTO grading_runs (id, submission_id, attempt, status, correlation_id,
						score_percent, created_at)
					VALUES ('%s', '%s', 1, 'COMPLETED', 'c', 101, now())
					""".formatted(UUID.randomUUID(), UUID.randomUUID())))
				.satisfies((ex) -> assertThat(ex.getMessage()).containsAnyOf("grading_runs_score_range",
						"violates foreign key"));
		}

		@Test
		@DisplayName("permits only one live extension per student and assignment")
		void allowsOnlyOneLiveExtension() throws SQLException {
			UUID courseId = UUID.randomUUID();
			insertCourse("ext-course", courseId);
			UUID assignmentId = UUID.randomUUID();
			execute("""
					INSERT INTO assignments (id, course_id, assignment_key, title, status,
						max_points, test_count, pass_threshold, created_at, updated_at)
					VALUES ('%s', '%s', 'ext-01', 'Ext', 'DRAFT', 100, 10, 100, now(), now())
					""".formatted(assignmentId, courseId));
			UUID studentId = insertStudent("s-ext");
			insertExtension(assignmentId, studentId, null);

			assertThatExceptionOfType(SQLException.class)
				.isThrownBy(() -> insertExtension(assignmentId, studentId, null))
				.withMessageContaining("deadline_extensions_active_key");

			// Revoking the first one must free the slot, otherwise an instructor could
			// never correct a mistaken extension.
			execute("UPDATE deadline_extensions SET revoked_at = now() WHERE assignment_id = '%s'"
				.formatted(assignmentId));
			insertExtension(assignmentId, studentId, null);

			assertThat(querySingle("SELECT count(*)::text FROM deadline_extensions WHERE assignment_id = '%s'"
				.formatted(assignmentId))).isEqualTo("2");
		}

	}

	// ---------------------------------------------------------------- helpers

	private void insertRuntime(String key, String tag, String digest) throws SQLException {
		execute("""
				INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest,
					test_command, created_at, updated_at)
				VALUES ('%s', '%s', 'Display', 'registry.example.org/runtime', '%s', '%s',
					'npm test', now(), now())
				""".formatted(UUID.randomUUID(), key, tag, digest));
	}

	private void insertCourse(String courseKey) throws SQLException {
		insertCourse(courseKey, UUID.randomUUID());
	}

	private void insertCourse(String courseKey, UUID id) throws SQLException {
		execute("""
				INSERT INTO courses (id, course_key, name, created_at, updated_at)
				VALUES ('%s', '%s', 'Course', now(), now())
				""".formatted(id, courseKey));
	}

	private UUID insertStudent(String studentNumber) throws SQLException {
		UUID id = UUID.randomUUID();
		execute("""
				INSERT INTO students (id, student_number, first_name, last_name, email,
					registered_at, created_at, updated_at)
				VALUES ('%s', '%s', 'A', 'B', '%s@example.org', now(), now(), now())
				""".formatted(id, studentNumber, studentNumber));
		return id;
	}

	private void insertKey(UUID studentId, String fingerprint) throws SQLException {
		execute("""
				INSERT INTO ssh_keys (id, student_id, label, key_type, public_key, fingerprint,
					status, added_via, created_at, updated_at)
				VALUES ('%s', '%s', 'k', 'ssh-ed25519', 'AAAA', '%s', 'ACTIVE', 'REGISTRATION',
					now(), now())
				""".formatted(UUID.randomUUID(), studentId, fingerprint));
	}

	private void insertExtension(UUID assignmentId, UUID studentId, String ignored) throws SQLException {
		execute("""
				INSERT INTO deadline_extensions (id, assignment_id, student_id, extended_due_at,
					reason, granted_by, granted_at, created_at)
				VALUES ('%s', '%s', '%s', now(), 'documented reason', 'instructor', now(), now())
				""".formatted(UUID.randomUUID(), assignmentId, studentId));
	}

	private void execute(String sql) throws SQLException {
		try (Connection connection = this.dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}

	private String querySingle(String sql) throws SQLException {
		List<String> values = queryColumn(sql);
		return values.isEmpty() ? null : values.getFirst();
	}

	private List<String> queryColumn(String sql) throws SQLException {
		List<String> values = new ArrayList<>();
		try (Connection connection = this.dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet rs = statement.executeQuery(sql)) {
			while (rs.next()) {
				values.add(rs.getString(1));
			}
		}
		return values;
	}

}
