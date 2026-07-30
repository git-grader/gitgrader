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

package org.gitgrader.courses;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Public read model of a course.
 *
 * @param id course identifier
 * @param courseKey clone URL and filesystem-safe key
 * @param name display name
 * @param description optional description
 * @param semester optional semester label
 * @param startsOn optional start date
 * @param endsOn optional end date
 * @param timezone display timezone identifier
 * @param status lifecycle state
 * @param registrationOpensAt optional registration opening time
 * @param registrationClosesAt optional registration closing time
 * @param registrationEnabled whether registration is enabled
 */
public record CourseView(UUID id, String courseKey, String name, @Nullable String description,
		@Nullable String semester, @Nullable LocalDate startsOn, @Nullable LocalDate endsOn, String timezone,
		CourseStatus status, @Nullable Instant registrationOpensAt, @Nullable Instant registrationClosesAt,
		boolean registrationEnabled) {
}
