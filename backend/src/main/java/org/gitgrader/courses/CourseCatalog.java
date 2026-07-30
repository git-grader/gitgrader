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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Provides read-only access to courses, classes, and enrollments. */
public interface CourseCatalog {

	/**
	 * Finds a course by identifier.
	 * @param id course identifier
	 * @return matching course, if present
	 */
	Optional<CourseView> findCourse(UUID id);

	/**
	 * Lists courses by lifecycle state.
	 * @param status course status
	 * @param pageable requested page
	 * @return matching courses
	 */
	Page<CourseView> findCourses(CourseStatus status, Pageable pageable);

	/**
	 * Lists classes in a course.
	 * @param courseId course identifier
	 * @return course classes
	 */
	List<CourseClassView> findClasses(UUID courseId);

	/**
	 * Lists enrollments for a student.
	 * @param studentId student identifier
	 * @return student enrollments
	 */
	List<EnrollmentView> findEnrollments(UUID studentId);

}
