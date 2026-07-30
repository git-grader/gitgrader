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
import java.util.UUID;

import org.gitgrader.identity.StudentDirectory;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultCourseServiceTest {

	@Test
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

}
