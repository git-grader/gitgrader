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
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.gitgrader.courses.CourseClassView;

/** Class grouping owned by one course. */
@Entity
@Table(name = "course_classes")
public class CourseClass {

	@Id
	private UUID id;

	@Column(name = "course_id", nullable = false)
	private UUID courseId;

	@Column(name = "class_key", nullable = false)
	private String classKey;

	@Column(nullable = false)
	private String name;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected CourseClass() {
	}

	/**
	 * Creates a class in a course.
	 * @param courseId owning course identifier
	 * @param classKey course-local key
	 * @param name display name
	 * @param clock source of creation time
	 */
	public CourseClass(UUID courseId, String classKey, String name, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.courseId = courseId;
		this.classKey = classKey;
		this.name = name;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Determines whether this class belongs to a course.
	 * @param candidateCourseId course to compare
	 * @return true when identifiers match
	 */
	public boolean belongsTo(UUID candidateCourseId) {
		return this.courseId.equals(candidateCourseId);
	}

	/**
	 * Returns the class identifier.
	 * @return class identifier
	 */
	public UUID id() {
		return this.id;
	}

	/**
	 * Converts this entity to its public read model.
	 * @return class view
	 */
	public CourseClassView toView() {
		return new CourseClassView(this.id, this.courseId, this.classKey, this.name);
	}

}
