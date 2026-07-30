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
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.gitgrader.grading.TestOutcome;
import org.jspecify.annotations.Nullable;

/**
 * The outcome of one test within a grading run.
 *
 * <p>
 * <strong>Two names, on purpose.</strong> {@link #testName} is what the report emitted
 * and {@link #internalMessage} is the raw assertion output; for a hidden test both reveal
 * the secret and are instructor-only. {@link #publicName} and {@link #studentMessage} are
 * the sanitised pair that a student may see. Keeping them in separate columns means the
 * redaction decision is made once, at write time, rather than being re-derived correctly
 * at every read.
 */
@Entity
@Table(name = "test_results")
public class TestResultRecord {

	/** Marks a test whose name and output a student may see. */
	public static final String VISIBILITY_PUBLIC = "PUBLIC";

	/** Marks a test whose name and output must never reach a student. */
	public static final String VISIBILITY_HIDDEN = "HIDDEN";

	@Id
	private UUID id;

	@Column(name = "grading_run_id", nullable = false, updatable = false)
	private UUID gradingRunId;

	@Column(nullable = false, updatable = false)
	private String visibility;

	@Column(updatable = false)
	private @Nullable String category;

	@Column(name = "test_name", updatable = false)
	private @Nullable String testName;

	@Column(name = "public_name", updatable = false)
	private @Nullable String publicName;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, updatable = false)
	private TestOutcome outcome;

	@Column(nullable = false, updatable = false)
	private BigDecimal weight;

	@Column(name = "duration_ms", updatable = false)
	private @Nullable Long durationMs;

	@Column(name = "student_message", updatable = false)
	private @Nullable String studentMessage;

	@Column(name = "internal_message", updatable = false)
	private @Nullable String internalMessage;

	@Column(name = "display_order", nullable = false, updatable = false)
	private int displayOrder;

	protected TestResultRecord() {
		// Required by JPA.
	}

	@SuppressWarnings("checkstyle:ParameterNumber") // one field per column, all immutable
	public TestResultRecord(UUID gradingRunId, String visibility, @Nullable String category, @Nullable String testName,
			@Nullable String publicName, TestOutcome outcome, BigDecimal weight, @Nullable Long durationMs,
			@Nullable String studentMessage, @Nullable String internalMessage, int displayOrder) {
		this.id = UUID.randomUUID();
		this.gradingRunId = gradingRunId;
		this.visibility = visibility;
		this.category = category;
		this.testName = testName;
		this.publicName = publicName;
		this.outcome = outcome;
		this.weight = weight;
		this.durationMs = durationMs;
		this.studentMessage = studentMessage;
		this.internalMessage = internalMessage;
		this.displayOrder = displayOrder;
	}

	public UUID id() {
		return this.id;
	}

	public UUID gradingRunId() {
		return this.gradingRunId;
	}

	public String visibility() {
		return this.visibility;
	}

	public @Nullable String category() {
		return this.category;
	}

	public @Nullable String testName() {
		return this.testName;
	}

	public @Nullable String publicName() {
		return this.publicName;
	}

	public TestOutcome outcome() {
		return this.outcome;
	}

	public BigDecimal weight() {
		return this.weight;
	}

	public @Nullable Long durationMs() {
		return this.durationMs;
	}

	public @Nullable String studentMessage() {
		return this.studentMessage;
	}

	public @Nullable String internalMessage() {
		return this.internalMessage;
	}

	public int displayOrder() {
		return this.displayOrder;
	}

	/**
	 * Whether this test's real name and output must be withheld from the student.
	 * @return true for a hidden test
	 */
	public boolean isHidden() {
		return VISIBILITY_HIDDEN.equals(this.visibility);
	}

}
