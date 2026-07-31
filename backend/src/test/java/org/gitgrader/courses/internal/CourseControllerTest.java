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
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.api.GlobalExceptionHandler;
import org.gitgrader.courses.CourseDefinition;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.domain.Course;
import org.gitgrader.courses.domain.CourseClass;
import org.gitgrader.courses.web.CourseController;
import org.gitgrader.courses.web.CourseExceptionHandler;
import org.gitgrader.identity.StudentDirectory;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CourseControllerTest {

	private static final Instant NOW = Instant.parse("2026-03-01T10:15:30Z");

	private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

	@Test
	void updatingCourseChangesStatusAndRegistrationFlag() throws Exception {
		Course course = course("java-101", CourseStatus.DRAFT, false);

		mockMvc(course, null)
			.perform(put("/api/v1/courses/{id}", course.id()).contentType("application/json")
				.content(definition("java-101", "Updated Java", CourseStatus.ACTIVE, true)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Updated Java"))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.registrationEnabled").value(true));
	}

	@Test
	void changingCourseKeyReturns400() throws Exception {
		Course course = course("java-101", CourseStatus.DRAFT, false);

		mockMvc(course, null)
			.perform(put("/api/v1/courses/{id}", course.id()).contentType("application/json")
				.content(definition("java-102", "Java", CourseStatus.ACTIVE, true)))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("courseKey cannot be changed"));
	}

	@Test
	void updatingUnknownCourseReturns404() throws Exception {
		mockMvc(null, null)
			.perform(put("/api/v1/courses/{id}", UUID.randomUUID()).contentType("application/json")
				.content(definition("java-101", "Java", CourseStatus.ACTIVE, true)))
			.andExpect(status().isNotFound());
	}

	@Test
	void updatingClassChangesName() throws Exception {
		Course course = course("java-101", CourseStatus.ACTIVE, true);
		CourseClass courseClass = new CourseClass(course.id(), "class-a", "Class A", CLOCK);

		mockMvc(course, courseClass)
			.perform(put("/api/v1/courses/{id}/classes/{classId}", course.id(), courseClass.id())
				.contentType("application/json")
				.content("{\"classKey\":\"class-a\",\"name\":\"Renamed Class\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.classKey").value("class-a"))
			.andExpect(jsonPath("$.name").value("Renamed Class"));
	}

	@Test
	void updatingClassThroughTheWrongCourseReturns404() throws Exception {
		Course course = course("java-101", CourseStatus.ACTIVE, true);
		Course otherCourse = course("python-201", CourseStatus.ACTIVE, true);
		CourseClass courseClass = new CourseClass(otherCourse.id(), "class-a", "Class A", CLOCK);

		mockMvc(course, courseClass)
			.perform(put("/api/v1/courses/{id}/classes/{classId}", course.id(), courseClass.id())
				.contentType("application/json")
				.content("{\"classKey\":\"class-a\",\"name\":\"Renamed Class\"}"))
			.andExpect(status().isNotFound());
	}

	@Test
	void changingClassKeyReturns400() throws Exception {
		Course course = course("java-101", CourseStatus.ACTIVE, true);
		CourseClass courseClass = new CourseClass(course.id(), "class-a", "Class A", CLOCK);

		mockMvc(course, courseClass)
			.perform(put("/api/v1/courses/{id}/classes/{classId}", course.id(), courseClass.id())
				.contentType("application/json")
				.content("{\"classKey\":\"class-b\",\"name\":\"Class B\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("classKey cannot be changed"));
	}

	private static MockMvc mockMvc(Course course, CourseClass courseClass) {
		CourseRepository courses = mock(CourseRepository.class);
		CourseClassRepository classes = mock(CourseClassRepository.class);
		if (course != null) {
			when(courses.findById(course.id())).thenReturn(Optional.of(course));
		}
		if (courseClass != null) {
			when(classes.findById(courseClass.id())).thenReturn(Optional.of(courseClass));
		}
		when(courses.save(any(Course.class))).thenAnswer((invocation) -> invocation.getArgument(0));
		when(classes.save(any(CourseClass.class))).thenAnswer((invocation) -> invocation.getArgument(0));
		DefaultCourseService service = new DefaultCourseService(courses, classes, mock(EnrollmentRepository.class),
				mock(StudentDirectory.class), CLOCK);
		return MockMvcBuilders.standaloneSetup(new CourseController(service, service))
			.setControllerAdvice(new GlobalExceptionHandler(), new CourseExceptionHandler())
			.build();
	}

	private static Course course(String courseKey, CourseStatus status, boolean registrationEnabled) {
		return new Course(new CourseDefinition(courseKey, "Java", null, null, null, null, "UTC", status, null, null,
				registrationEnabled), CLOCK);
	}

	private static String definition(String courseKey, String name, CourseStatus status, boolean registrationEnabled) {
		return """
				{
				  "courseKey":"%s",
				  "name":"%s",
				  "description":"Updated description",
				  "semester":"Spring 2026",
				  "startsOn":"2026-03-01",
				  "endsOn":"2026-06-30",
				  "timezone":"UTC",
				  "status":"%s",
				  "registrationOpensAt":null,
				  "registrationClosesAt":null,
				  "registrationEnabled":%s
				}
				""".formatted(courseKey, name, status, registrationEnabled);
	}

}
