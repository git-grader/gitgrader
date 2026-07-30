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

package org.gitgrader.grading.domain;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.gitgrader.grading.FailureCategory;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.GradingScore;
import org.jspecify.annotations.Nullable;

/**
 * One attempt at grading a submission.
 *
 * <p>
 * Re-grading appends a run with the next {@code attempt} number and never touches the
 * previous one. That is what makes a disputed grade defensible months later: the original
 * run still carries the runtime image digest, the test suite version and the score it
 * actually produced.
 *
 * <p>
 * {@link #failureCategory} exists to keep an infrastructure failure from ever being read
 * as a bad submission. A run that could not pull its image is not a student who wrote
 * failing code, and the two must not share a status.
 */
@Entity
@Table(name = "grading_runs")
public class GradingRun {

	/** Version of the scoring algorithm, recorded so an old run stays explainable. */
	private static final String ALGORITHM_VERSION = "v1";

	@Id
	private UUID id;

	@Column(name = "submission_id", nullable = false, updatable = false)
	private UUID submissionId;

	@Column(nullable = false, updatable = false)
	private int attempt;

	// "trigger" is a reserved word in PostgreSQL and has to be quoted, otherwise
	// Hibernate emits DDL-valid but query-invalid SQL against this column.
	@Column(name = "\"trigger\"", nullable = false, updatable = false)
	private String trigger;

	@Column(name = "triggered_by", updatable = false)
	private @Nullable String triggeredBy;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GradingRunStatus status;

	@Column(name = "runtime_id", updatable = false)
	private @Nullable UUID runtimeId;

	@Column(name = "runtime_image_digest", updatable = false)
	private @Nullable String runtimeImageDigest;

	@Column(name = "test_suite_version_id", updatable = false)
	private @Nullable UUID testSuiteVersionId;

	@Column(name = "grading_algorithm_version", nullable = false, updatable = false)
	private String gradingAlgorithmVersion;

	@Column(name = "tests_total", nullable = false)
	private int testsTotal;

	@Column(name = "tests_passed", nullable = false)
	private int testsPassed;

	@Column(name = "tests_failed", nullable = false)
	private int testsFailed;

	@Column(name = "tests_errored", nullable = false)
	private int testsErrored;

	@Column(name = "tests_skipped", nullable = false)
	private int testsSkipped;

	@Column(name = "score_percent")
	private @Nullable BigDecimal scorePercent;

	@Column(name = "points_awarded")
	private @Nullable BigDecimal pointsAwarded;

	@Column
	private @Nullable Boolean passed;

	@Column(name = "exit_code")
	private @Nullable Integer exitCode;

	@Column(name = "duration_ms")
	private @Nullable Long durationMs;

	@Column(name = "correlation_id", nullable = false, updatable = false)
	private String correlationId;

	@Enumerated(EnumType.STRING)
	@Column(name = "failure_category")
	private @Nullable FailureCategory failureCategory;

	@Column(name = "failure_detail")
	private @Nullable String failureDetail;

	@Column(name = "started_at")
	private @Nullable Instant startedAt;

	@Column(name = "finished_at")
	private @Nullable Instant finishedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected GradingRun() {
		// Required by JPA.
	}

	public GradingRun(UUID submissionId, int attempt, String trigger, @Nullable UUID runtimeId,
			@Nullable String runtimeImageDigest, @Nullable UUID testSuiteVersionId, String correlationId, Clock clock) {
		this.id = UUID.randomUUID();
		this.submissionId = submissionId;
		this.attempt = attempt;
		this.trigger = trigger;
		this.status = GradingRunStatus.QUEUED;
		this.runtimeId = runtimeId;
		this.runtimeImageDigest = runtimeImageDigest;
		this.testSuiteVersionId = testSuiteVersionId;
		this.gradingAlgorithmVersion = ALGORITHM_VERSION;
		this.correlationId = correlationId;
		this.createdAt = Instant.now(clock);
	}

	public UUID id() {
		return this.id;
	}

	public UUID submissionId() {
		return this.submissionId;
	}

	public int attempt() {
		return this.attempt;
	}

	public GradingRunStatus status() {
		return this.status;
	}

	public String correlationId() {
		return this.correlationId;
	}

	public @Nullable BigDecimal scorePercent() {
		return this.scorePercent;
	}

	public @Nullable BigDecimal pointsAwarded() {
		return this.pointsAwarded;
	}

	public int testsPassed() {
		return this.testsPassed;
	}

	public int testsTotal() {
		return this.testsTotal;
	}

	public @Nullable Boolean passed() {
		return this.passed;
	}

	public @Nullable FailureCategory failureCategory() {
		return this.failureCategory;
	}

	/**
	 * Marks the run as executing.
	 * @param clock the application clock
	 */
	public void markRunning(Clock clock) {
		this.status = GradingRunStatus.RUNNING;
		this.startedAt = Instant.now(clock);
	}

	/**
	 * Records a completed run and its score.
	 * @param score the computed score
	 * @param exitCode the sandbox exit code
	 * @param durationMs how long the sandbox ran
	 * @param clock the application clock
	 */
	public void complete(GradingScore score, @Nullable Integer exitCode, long durationMs, Clock clock) {
		this.status = GradingRunStatus.COMPLETED;
		this.testsTotal = score.testsTotal();
		this.testsPassed = score.testsPassed();
		this.testsFailed = score.testsFailed();
		this.testsErrored = score.testsErrored();
		this.testsSkipped = score.testsSkipped();
		this.scorePercent = score.scorePercent();
		this.pointsAwarded = score.pointsAwarded();
		this.passed = score.passed();
		this.exitCode = exitCode;
		this.durationMs = durationMs;
		this.finishedAt = Instant.now(clock);
		this.failureCategory = score.passed() ? null : FailureCategory.STUDENT_TEST_FAILURE;
	}

	/**
	 * Records a run that could not produce a score.
	 *
	 * <p>
	 * Leaves {@link #scorePercent} null rather than writing a zero. A zero would be
	 * indistinguishable from a student who passed nothing, and would silently become a
	 * grade.
	 * @param category what kind of failure this was
	 * @param detail an instructor-facing explanation
	 * @param status the terminal status
	 * @param clock the application clock
	 */
	public void fail(FailureCategory category, String detail, GradingRunStatus status, Clock clock) {
		this.status = status;
		this.failureCategory = category;
		this.failureDetail = detail;
		this.finishedAt = Instant.now(clock);
	}

}
