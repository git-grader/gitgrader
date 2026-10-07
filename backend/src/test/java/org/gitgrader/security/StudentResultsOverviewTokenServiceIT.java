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

package org.gitgrader.security;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.gitgrader.identity.StudentRegistration;
import org.gitgrader.identity.StudentRegistry;
import org.gitgrader.identity.StudentView;
import org.gitgrader.security.domain.StudentResultsOverviewToken;
import org.gitgrader.security.domain.StudentResultsOverviewToken.Status;
import org.gitgrader.security.internal.StudentResultsOverviewTokenRepository;
import org.gitgrader.testsupport.EnabledIfDockerAvailable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Exercises the overview-token mint against the PostgreSQL locking rules it depends on.
 *
 * <p>
 * <strong>Why this is an integration test.</strong> The mint rotates a single row per
 * student behind the partial unique index {@code uq_srot_one_active_per_student}, and
 * whether the rotation returns at all is a statement about what PostgreSQL does when two
 * transactions touch that row. A mocked repository carries none of that: the unit test
 * next door can assert the shape of the retry loop while the loop it describes blocks a
 * thread forever.
 *
 * <p>
 * That is not hypothetical. Splitting the retire and the insert across two transactions
 * made every mint for a student who already held a token wait on a lock held by its own
 * thread, so the push never completed, the thread kept its pool connections, and once the
 * pool was exhausted even SSH public key authentication stopped working - the grader
 * refused every clone in the course.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@EnabledIfDockerAvailable
// The context is closed with the class, while this container is still up. Left cached,
// it outlived the database it was pointed at and every shutdown hook then blocked for
// the full connection timeout, which is what made the JVM miss its own exit.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StudentResultsOverviewTokenServiceIT {

	/**
	 * A mint that has to lose a race once still returns immediately; anything slower than
	 * this is the block this test exists to catch, not a slow machine.
	 */
	private static final Duration MINT_TIMEOUT = Duration.ofSeconds(20);

	@Container
	@SuppressWarnings("resource")
	static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.2-alpine")
		.withDatabaseName("gitgrader")
		.withUsername("gitgrader")
		.withPassword("gitgrader");

	@Autowired
	private StudentResultsOverviewTokenService service;

	@Autowired
	private StudentResultsOverviewTokenRepository repository;

	@Autowired
	private StudentRegistry students;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	/**
	 * The student the seeded token belongs to. Registered per test because the unique
	 * constraints on {@code students} make one shared row fail the second test.
	 */
	private UUID studentId;

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
		registry.add("spring.datasource.username", POSTGRES::getUsername);
		registry.add("spring.datasource.password", POSTGRES::getPassword);
	}

	/**
	 * Cancels any backend still waiting on a lock.
	 *
	 * <p>
	 * The regression this test guards against blocks forever rather than failing, and a
	 * blocked backend never returns its pool connections, which is how a failed assertion
	 * would turn into a build that never finishes. Cancelling turns the stranded thread
	 * into an ordinary error the pool can close behind.
	 */
	@AfterEach
	void releaseStrandedBackends() {
		List<Long> waiting = this.jdbcTemplate.queryForList(
				"SELECT pid FROM pg_stat_activity "
						+ "WHERE datname = current_database() AND wait_event_type = 'Lock' AND pid <> pg_backend_pid()",
				Long.class);
		waiting.forEach((pid) -> this.jdbcTemplate.queryForObject("SELECT pg_cancel_backend(?)", Long.class, pid));
	}

	@Test
	@DisplayName("rotates the token a student already holds instead of blocking on it")
	void mintsForAStudentWhoAlreadyHoldsAnActiveToken() throws Exception {
		StudentResultsOverviewToken previous = seedActiveToken();

		String token = mintWithinTimeout();

		// The old link must be withdrawn and the new one live, in one transaction that
		// actually completed.
		assertThat(token).isNotBlank();
		assertThat(activeTokens()).hasSize(1);
		StudentResultsOverviewToken rotated = this.repository.findById(previous.id()).orElseThrow();
		assertThat(rotated.status()).isEqualTo(Status.REVOKED);
		assertThat(rotated.tokenHash()).isNotEqualTo(TokenHash.of(token));
	}

	@Test
	@DisplayName("two pushes racing for the same student both come back with a token")
	void concurrentMintsBothReturn() throws Exception {
		seedActiveToken();

		List<String> tokens = new ArrayList<>();
		ExecutorService racers = Executors.newFixedThreadPool(2, daemonNamed("overview-token-racer"));
		try {
			List<Future<String>> outcomes = new ArrayList<>();
			for (int i = 0; i < 2; i++) {
				outcomes.add(racers.submit(() -> this.service.issueForStudent(this.studentId)));
			}
			for (Future<String> outcome : outcomes) {
				tokens.add(outcome.get(MINT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS));
			}
		}
		finally {
			racers.shutdownNow();
		}

		// Losing the race costs a retry, not the push: both callers hold a usable token
		// and the index still admits exactly one active row.
		assertThat(tokens).hasSize(2);
		assertThat(tokens.get(0)).isNotBlank().isNotEqualTo(tokens.get(1));
		assertThat(activeTokens()).hasSize(1);
	}

	private String mintWithinTimeout() throws Exception {
		ExecutorService mint = Executors.newSingleThreadExecutor(daemonNamed("overview-token-mint"));
		try {
			Future<String> outcome = mint.submit(() -> this.service.issueForStudent(this.studentId));
			return outcome.get(MINT_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			throw new AssertionError("issueForStudent returned nothing within " + MINT_TIMEOUT
					+ "; a mint that blocks holds its connection for as long as it blocks", ex);
		}
		finally {
			mint.shutdownNow();
		}
	}

	private static ThreadFactory daemonNamed(String name) {
		return (task) -> {
			Thread thread = new Thread(task, name);
			// Daemon so that a mint which never returns fails the test rather than
			// keeping the JVM alive after it.
			thread.setDaemon(true);
			return thread;
		};
	}

	/**
	 * Registers a student and gives them an already-active token, which is the state
	 * every second push arrives in and the only state the rotation can deadlock on.
	 * @return the token the student is holding before the mint under test runs
	 */
	private StudentResultsOverviewToken seedActiveToken() {
		String username = "srot-" + UUID.randomUUID();
		StudentView student = this.students
			.register(new StudentRegistration(username, "Given", "Family", username + "@example.org", null, null));
		this.studentId = student.id();

		Instant createdAt = Instant.parse("2026-01-01T10:00:00Z");
		StudentResultsOverviewToken token = new StudentResultsOverviewToken(UUID.randomUUID(), this.studentId,
				TokenHash.of("pre-existing-overview-token-" + this.studentId), "pre-exis", createdAt,
				createdAt.plus(Duration.ofDays(180)), Status.ACTIVE);
		return this.repository.save(token);
	}

	private List<StudentResultsOverviewToken> activeTokens() {
		return this.repository.findAll()
			.stream()
			.filter((token) -> token.isActive() && this.studentId.equals(token.studentId()))
			.toList();
	}

}
