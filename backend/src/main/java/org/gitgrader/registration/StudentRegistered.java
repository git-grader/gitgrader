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

package org.gitgrader.registration;

import java.time.Instant;
import java.util.UUID;

/**
 * Published once a student has been accepted onto a course.
 *
 * <p>
 * Carries the course key and student number rather than only identifiers, because a
 * repository path is built from those two values and a listener would otherwise have to
 * reach back into two more modules to reconstruct something already known here.
 *
 * @param studentId the new student
 * @param studentNumber the student number, as it appears in repository paths
 * @param courseId the course the student joined
 * @param courseKey the course key, as it appears in repository paths
 * @param registeredAt when the registration was accepted
 */
public record StudentRegistered(UUID studentId, String studentNumber, UUID courseId, String courseKey,
		Instant registeredAt) {
}
