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

import org.jspecify.annotations.Nullable;

/** Creates courses, classes, and student enrollments. */
public interface CourseAdministration {

	/**
	 * Creates a course.
	 * @param definition course values
	 * @return created course
	 */
	CourseView createCourse(CourseDefinition definition);

	/**
	 * Updates a course while preserving its stable key.
	 * @param id course identifier
	 * @param definition replacement course values
	 * @return updated course
	 */
	CourseView update(UUID id, CourseDefinition definition);

	/**
	 * Adds a class to a course.
	 * @param courseId course identifier
	 * @param classKey course-local class key
	 * @param name class display name
	 * @return created class
	 */
	CourseClassView createClass(UUID courseId, String classKey, String name);

	/**
	 * Updates a class of a course while preserving its stable key.
	 * @param courseId owning course identifier
	 * @param classId class identifier
	 * @param classKey stable course-local class key
	 * @param name replacement display name
	 * @return updated class
	 */
	CourseClassView updateClass(UUID courseId, UUID classId, String classKey, String name);

	/**
	 * Enrolls one student once in a course and optional class.
	 * @param studentId student identifier
	 * @param courseId course identifier
	 * @param classId optional class identifier
	 * @return created enrollment
	 */
	EnrollmentView enroll(UUID studentId, UUID courseId, @Nullable UUID classId);

}
