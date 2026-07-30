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
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * One student's progress in a course.
 *
 * @param studentId student identifier
 * @param studentNumber institutional student number
 * @param fullName student display name
 * @param fullyCompleted mandatory assignments meeting their pass threshold
 * @param partiallyCompleted mandatory assignments attempted below their pass threshold
 * @param notStarted mandatory assignments without a student-attributable attempt
 * @param completionRate fully completed mandatory assignments divided by mandatory
 * assignments
 * @param pointsEarned points earned across assignments
 * @param pointsRate points earned divided by points available
 * @param totalPoints points available across assignments, the denominator behind
 * pointsRate
 * @param submissionCount student-attributable submissions
 * @param lastActivityAt most recent student-attributable submission time
 * @param assignments progress indexed by assignment key
 */
public record StudentProgressRow(UUID studentId, String studentNumber, String fullName, int fullyCompleted,
		int partiallyCompleted, int notStarted, BigDecimal completionRate, BigDecimal pointsEarned,
		BigDecimal pointsRate, BigDecimal totalPoints, long submissionCount, @Nullable Instant lastActivityAt,
		Map<String, AssignmentProgress> assignments) {

	public StudentProgressRow {
		assignments = Map.copyOf(assignments);
	}

	/**
	 * Progress for one assignment.
	 *
	 * @param percent best graded percentage, represented from zero through one hundred
	 * @param points awarded points
	 */
	public record AssignmentProgress(BigDecimal percent, BigDecimal points) {
	}

}
