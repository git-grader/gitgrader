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
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.gitgrader.courses.CourseAdministration;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseClassView;
import org.gitgrader.courses.CourseDefinition;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.courses.EnrollmentView;
import org.gitgrader.courses.domain.Course;
import org.gitgrader.courses.domain.CourseClass;
import org.gitgrader.courses.domain.Enrollment;
import org.gitgrader.identity.StudentDirectory;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default course catalog and administration implementation. */
@Service
@Transactional
public class DefaultCourseService implements CourseCatalog, CourseAdministration {

	private final CourseRepository courses;

	private final CourseClassRepository classes;

	private final EnrollmentRepository enrollments;

	private final StudentDirectory students;

	private final Clock clock;

	DefaultCourseService(CourseRepository courses, CourseClassRepository classes, EnrollmentRepository enrollments,
			StudentDirectory students, Clock clock) {
		this.courses = courses;
		this.classes = classes;
		this.enrollments = enrollments;
		this.students = students;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<CourseView> findCourse(UUID id) {
		return this.courses.findById(id).map(Course::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<CourseView> findCourses(CourseStatus status, Pageable pageable) {
		return this.courses.findByStatus(status, pageable).map(Course::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<CourseClassView> findClasses(UUID courseId) {
		return this.classes.findByCourseId(courseId).stream().map(CourseClass::toView).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<EnrollmentView> findEnrollments(UUID studentId) {
		return this.enrollments.findByStudentId(studentId).stream().map(Enrollment::toView).toList();
	}

	@Override
	public CourseView createCourse(CourseDefinition definition) {
		return this.courses.save(new Course(definition, this.clock)).toView();
	}

	@Override
	public CourseClassView createClass(UUID courseId, String classKey, String name) {
		requireCourse(courseId);
		return this.classes.save(new CourseClass(courseId, classKey, name, this.clock)).toView();
	}

	@Override
	public EnrollmentView enroll(UUID studentId, UUID courseId, @Nullable UUID classId) {
		if (this.enrollments.existsByStudentIdAndCourseId(studentId, courseId)) {
			throw new IllegalStateException("Student is already enrolled in this course");
		}
		if (this.students.findById(studentId).isEmpty()) {
			throw new EntityNotFoundException("Student not found: " + studentId);
		}
		Course course = requireCourse(courseId);
		CourseClass courseClass = classId == null ? null : requireClass(classId);
		return this.enrollments.save(new Enrollment(studentId, course, courseClass, this.clock)).toView();
	}

	private Course requireCourse(UUID id) {
		return this.courses.findById(id).orElseThrow(() -> new EntityNotFoundException("Course not found: " + id));
	}

	private CourseClass requireClass(UUID id) {
		return this.classes.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Course class not found: " + id));
	}

}
