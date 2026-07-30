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
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Public read model of a course enrollment.
 *
 * @param id enrollment identifier
 * @param studentId student identifier
 * @param courseId course identifier
 * @param classId optional class identifier
 * @param status lifecycle state
 * @param enrolledAt enrollment time
 */
public record EnrollmentView(UUID id, UUID studentId, UUID courseId, @Nullable UUID classId, EnrollmentStatus status,
		Instant enrolledAt) {
}
