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

package org.gitgrader.courses.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.gitgrader.identity.StudentDirectory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for the enrolment rules of {@link DefaultCourseService}.
 *
 * <p>
 * The duplicate check has to run before a row is written rather than relying on a
 * database constraint, so a rejected repeat enrollment leaves nothing behind to confuse
 * an instructor reading the roster. The roster query is verified down to the repository
 * method it calls, because a lookup scoped only by course would quietly include students
 * enrolled in a different class.
 */
class DefaultCourseServiceTest {

	@Test
	@DisplayName("rejects a duplicate enrollment before creating a second row")
	void duplicateEnrollmentIsRejectedBeforeCreatingAnotherRow() {
		CourseRepository courses = mock(CourseRepository.class);
		CourseClassRepository classes = mock(CourseClassRepository.class);
		EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
		StudentDirectory students = mock(StudentDirectory.class);
		Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);
		DefaultCourseService service = new DefaultCourseService(courses, classes, enrollments, students, clock);
		UUID studentId = UUID.randomUUID();
		UUID courseId = UUID.randomUUID();
		when(enrollments.existsByStudentIdAndCourseId(studentId, courseId)).thenReturn(true);

		assertThatThrownBy(() -> service.enroll(studentId, courseId, null)).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("already enrolled");
	}

	@Test
	@DisplayName("scopes enrolled student ids to the course and to the class ids")
	void enrolledStudentIdsAreScopedToTheCourseAndClassIds() {
		CourseRepository courses = mock(CourseRepository.class);
		CourseClassRepository classes = mock(CourseClassRepository.class);
		EnrollmentRepository enrollments = mock(EnrollmentRepository.class);
		StudentDirectory students = mock(StudentDirectory.class);
		Clock clock = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);
		DefaultCourseService service = new DefaultCourseService(courses, classes, enrollments, students, clock);
		UUID requestedCourseId = UUID.randomUUID();
		UUID requestedClassId = UUID.randomUUID();
		List<UUID> requestedStudents = List.of(UUID.randomUUID(), UUID.randomUUID());
		when(enrollments.findStudentIdsByCourseIdAndClassId(requestedCourseId, requestedClassId))
			.thenReturn(requestedStudents);

		assertThat(service.findEnrolledStudentIds(requestedCourseId, requestedClassId))
			.containsExactlyElementsOf(requestedStudents);
		verify(enrollments).findStudentIdsByCourseIdAndClassId(requestedCourseId, requestedClassId);
	}

}
