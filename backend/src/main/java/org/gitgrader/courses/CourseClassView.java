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

import java.util.UUID;

/**
 * Public read model of a class within a course.
 *
 * @param id class identifier
 * @param courseId owning course identifier
 * @param classKey course-local key
 * @param name display name
 */
public record CourseClassView(UUID id, UUID courseId, String classKey, String name) {
}
