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

package org.gitgrader.registration.internal;

import java.util.UUID;

import org.gitgrader.courses.CourseAdministration;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseClassView;
import org.gitgrader.registration.StudentRegistered;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Enrols a student on the course they registered for.
 *
 * <p>
 * Registering for a course is what enrolment means here, and nothing in the application
 * ever wrote one: {@code CourseAdministration.enroll} existed, no caller and no endpoint
 * reached it, and a self-registered student ended up with a repository per assignment and
 * no enrolment. Everything keyed on enrolment then saw an empty course - including the
 * course report, which is the instructor's only view of how the class is doing.
 *
 * <p>
 * Driven by the event rather than by the registration call for the same reason the
 * repositories are: a student is registered whether or not the bookkeeping that follows
 * succeeds, and the registration request should not carry it.
 */
@Component
class CourseEnrolment {

	private static final Logger logger = LoggerFactory.getLogger(CourseEnrolment.class);

	private final CourseAdministration courses;

	private final CourseCatalog catalog;

	CourseEnrolment(CourseAdministration courses, CourseCatalog catalog) {
		this.courses = courses;
		this.catalog = catalog;
	}

	@ApplicationModuleListener
	void onStudentRegistered(StudentRegistered event) {
		this.courses.enroll(event.studentId(), event.courseId(), classIdOf(event));
		logger.info("Enrolled student {} on course {}", event.studentId(), event.courseKey());
	}

	/**
	 * Resolves the class the student picked, which registration has already checked
	 * belongs to the course.
	 * @param event the registration that happened
	 * @return the class identifier, or {@code null} when the student picked none
	 */
	private @Nullable UUID classIdOf(StudentRegistered event) {
		String classKey = event.classKey();
		if (classKey == null || classKey.isBlank()) {
			return null;
		}
		return this.catalog.findClasses(event.courseId())
			.stream()
			.filter((candidate) -> candidate.classKey().equals(classKey))
			.map(CourseClassView::id)
			.findFirst()
			.orElse(null);
	}

}
