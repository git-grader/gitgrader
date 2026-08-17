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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.gitgrader.courses.CourseAdministration;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseClassView;
import org.gitgrader.registration.StudentRegistered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A student who registers for a course has to end up enrolled on it.
 *
 * <p>
 * {@code CourseAdministration.enroll} existed with no caller and no endpoint, so a
 * self-registered student got a repository per assignment and no enrolment. Everything
 * keyed on enrolment then saw an empty course, including the report an instructor uses to
 * see how the class is doing.
 */
class CourseEnrolmentTest {

	private static final UUID STUDENT = UUID.randomUUID();

	private static final UUID COURSE = UUID.randomUUID();

	private static final Instant WHEN = Instant.parse("2026-03-01T10:00:00Z");

	private CourseAdministration courses;

	private CourseCatalog catalog;

	private CourseEnrolment enrolment;

	@BeforeEach
	void setUp() {
		this.courses = mock(CourseAdministration.class);
		this.catalog = mock(CourseCatalog.class);
		this.enrolment = new CourseEnrolment(this.courses, this.catalog);
	}

	@Test
	@DisplayName("enrols the student on the class they picked")
	void enrolsOnTheChosenClass() {
		UUID classId = UUID.randomUUID();
		when(this.catalog.findClasses(COURSE))
			.thenReturn(List.of(new CourseClassView(classId, COURSE, "main", "Main class")));

		this.enrolment.onStudentRegistered(new StudentRegistered(STUDENT, "s1000", COURSE, "cs101", "main", WHEN));

		verify(this.courses).enroll(STUDENT, COURSE, classId);
	}

	@Test
	@DisplayName("enrols a student who picked no class, because a class is optional")
	void enrolsWithoutAClass() {
		this.enrolment.onStudentRegistered(new StudentRegistered(STUDENT, "s1000", COURSE, "cs101", null, WHEN));

		verify(this.courses).enroll(eq(STUDENT), eq(COURSE), isNull());
		verify(this.catalog, never()).findClasses(any());
	}

}
