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

package org.gitgrader.assignments;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Values used to create an assignment.
 *
 * @param courseId owning course
 * @param assignmentKey course-local stable key
 * @param title display title
 * @param description optional description
 * @param displayOrder course display order
 * @param status lifecycle status
 * @param mandatory whether completion is mandatory
 * @param opensAt optional opening instant
 * @param dueAt optional due instant
 * @param timezone optional display timezone
 * @param maxPoints maximum points
 * @param testCount configured test count
 * @param passThreshold percentage required to pass
 * @param allowLate whether late pushes are accepted
 * @param templateVersionId selected template version
 * @param testSuiteVersionId selected hidden test-suite version
 * @param runtimeId selected runtime
 * @param timeoutSeconds optional sandbox timeout
 * @param memoryLimitBytes optional memory limit
 * @param cpuLimit optional CPU limit
 * @param pidLimit optional process limit
 * @param networkEnabled whether sandbox networking is enabled
 */
public record AssignmentDefinition(UUID courseId, String assignmentKey, String title, @Nullable String description,
		int displayOrder, AssignmentStatus status, boolean mandatory, @Nullable Instant opensAt,
		@Nullable Instant dueAt, @Nullable String timezone, BigDecimal maxPoints, int testCount,
		BigDecimal passThreshold, boolean allowLate, @Nullable UUID templateVersionId,
		@Nullable UUID testSuiteVersionId, @Nullable UUID runtimeId, @Nullable Integer timeoutSeconds,
		@Nullable Long memoryLimitBytes, @Nullable BigDecimal cpuLimit, @Nullable Integer pidLimit,
		boolean networkEnabled) {
}
