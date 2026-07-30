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

package org.gitgrader.courses.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.gitgrader.courses.EnrollmentStatus;
import org.gitgrader.courses.EnrollmentView;
import org.jspecify.annotations.Nullable;

/** Student membership in one course and optional class. */
@Entity
@Table(name = "enrollments")
public class Enrollment {

	@Id
	private UUID id;

	@Column(name = "student_id", nullable = false)
	private UUID studentId;

	@Column(name = "course_id", nullable = false)
	private UUID courseId;

	@Column(name = "class_id")
	private @Nullable UUID classId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private EnrollmentStatus status;

	@Column(name = "enrolled_at", nullable = false)
	private Instant enrolledAt;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Enrollment() {
	}

	/**
	 * Creates an active enrollment after validating optional class ownership.
	 * @param studentId student identifier
	 * @param course course being joined
	 * @param courseClass optional class within the course
	 * @param clock source of enrollment time
	 */
	public Enrollment(UUID studentId, Course course, @Nullable CourseClass courseClass, Clock clock) {
		if (courseClass != null && !courseClass.belongsTo(course.id())) {
			throw new IllegalArgumentException("Selected class does not belong to the course");
		}
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.studentId = studentId;
		this.courseId = course.id();
		this.classId = courseClass == null ? null : courseClass.id();
		this.status = EnrollmentStatus.ACTIVE;
		this.enrolledAt = now;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Converts this entity to its public read model.
	 * @return enrollment view
	 */
	public EnrollmentView toView() {
		return new EnrollmentView(this.id, this.studentId, this.courseId, this.classId, this.status, this.enrolledAt);
	}

}
