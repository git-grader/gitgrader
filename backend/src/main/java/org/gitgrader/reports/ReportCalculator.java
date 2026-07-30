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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.gitgrader.submissions.SubmissionStatus;
import org.jspecify.annotations.Nullable;

/** Calculates a student progress row from read-only assessment facts. */
public final class ReportCalculator {

	private static final int RATE_SCALE = 6;

	private static final BigDecimal ONE_HUNDRED = BigDecimal.valueOf(100);

	private ReportCalculator() {
	}

	/**
	 * Calculates progress while excluding infrastructure failures from attempts.
	 * @param student student identity
	 * @param assignments course assignments
	 * @param submissions assessment facts
	 * @return calculated progress row
	 */
	public static StudentProgressRow calculate(Student student, List<Assignment> assignments,
			List<Assessment> submissions) {
		List<Assessment> attempts = submissions.stream().filter((item) -> item.status().isGraded()).toList();
		Map<String, StudentProgressRow.AssignmentProgress> progress = new LinkedHashMap<>();
		int fullyCompleted = 0;
		int partiallyCompleted = 0;
		int notStarted = 0;
		BigDecimal pointsEarned = BigDecimal.ZERO;

		for (Assignment assignment : assignments) {
			List<Assessment> graded = attempts.stream()
				.filter((item) -> item.assignmentId().equals(assignment.id()))
				.toList();
			BigDecimal bestPercent = graded.stream()
				.map(Assessment::percent)
				.max(BigDecimal::compareTo)
				.orElse(BigDecimal.ZERO);
			BigDecimal points = assignment.maxPoints()
				.multiply(bestPercent)
				.divide(ONE_HUNDRED, 2, RoundingMode.HALF_UP);
			pointsEarned = pointsEarned.add(points);
			progress.put(assignment.key(), new StudentProgressRow.AssignmentProgress(bestPercent, points));
			if (assignment.mandatory()) {
				if (graded.isEmpty()) {
					notStarted++;
				}
				else if (bestPercent.compareTo(assignment.passThreshold()) >= 0) {
					fullyCompleted++;
				}
				else {
					partiallyCompleted++;
				}
			}
		}

		int mandatoryCount = fullyCompleted + partiallyCompleted + notStarted;
		BigDecimal pointsAvailable = assignments.stream()
			.map(Assignment::maxPoints)
			.reduce(BigDecimal.ZERO, BigDecimal::add);
		Instant lastActivity = attempts.stream().map(Assessment::receivedAt).max(Instant::compareTo).orElse(null);
		return new StudentProgressRow(student.id(), student.studentNumber(), student.fullName(), fullyCompleted,
				partiallyCompleted, notStarted, rate(fullyCompleted, mandatoryCount), pointsEarned,
				rate(pointsEarned, pointsAvailable), pointsAvailable, attempts.size(), lastActivity, progress);
	}

	private static BigDecimal rate(long numerator, long denominator) {
		return denominator == 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator)
			.divide(BigDecimal.valueOf(denominator), RATE_SCALE, RoundingMode.HALF_UP);
	}

	private static BigDecimal rate(BigDecimal numerator, BigDecimal denominator) {
		return denominator.signum() == 0 ? BigDecimal.ZERO
				: numerator.divide(denominator, RATE_SCALE, RoundingMode.HALF_UP);
	}

	/**
	 * Minimal student identity needed by the calculator.
	 *
	 * @param id student identifier
	 * @param studentNumber institutional student number
	 * @param fullName display name
	 */
	public record Student(UUID id, String studentNumber, String fullName) {
	}

	/**
	 * Minimal assignment definition needed by the calculator.
	 *
	 * @param id assignment identifier
	 * @param key assignment key
	 * @param mandatory whether completion is mandatory
	 * @param maxPoints points available
	 * @param passThreshold percentage required for full completion
	 */
	public record Assignment(UUID id, String key, boolean mandatory, BigDecimal maxPoints, BigDecimal passThreshold) {
	}

	/**
	 * One submission's assessment facts.
	 *
	 * @param assignmentId assignment identifier
	 * @param status submission status
	 * @param percent awarded percentage, ignored unless status is graded
	 * @param receivedAt receive timestamp
	 */
	public record Assessment(UUID assignmentId, SubmissionStatus status, @Nullable BigDecimal percent,
			Instant receivedAt) {

		public Assessment {
			if (status.isGraded() && percent == null) {
				throw new IllegalArgumentException("A graded assessment requires a percentage");
			}
		}

		@Override
		public BigDecimal percent() {
			return this.percent == null ? BigDecimal.ZERO : this.percent;
		}

	}

}
