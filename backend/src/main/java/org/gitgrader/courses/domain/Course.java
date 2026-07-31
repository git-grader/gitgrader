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
import java.time.LocalDate;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.gitgrader.courses.CourseDefinition;
import org.gitgrader.courses.CourseIdentityMismatchException;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.jspecify.annotations.Nullable;

/** Course aggregate containing registration-window and key-safety rules. */
@Entity
@Table(name = "courses")
public class Course {

	private static final Pattern COURSE_KEY = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,62}[a-z0-9]$");

	@Id
	private UUID id;

	@Column(name = "course_key", nullable = false)
	private String courseKey;

	@Column(nullable = false)
	private String name;

	@Column
	private @Nullable String description;

	@Column
	private @Nullable String semester;

	@Column(name = "starts_on")
	private @Nullable LocalDate startsOn;

	@Column(name = "ends_on")
	private @Nullable LocalDate endsOn;

	@Column(nullable = false)
	private String timezone;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private CourseStatus status;

	@Column(name = "registration_opens_at")
	private @Nullable Instant registrationOpensAt;

	@Column(name = "registration_closes_at")
	private @Nullable Instant registrationClosesAt;

	@Column(name = "registration_enabled", nullable = false)
	private boolean registrationEnabled;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected Course() {
	}

	/**
	 * Creates a course after validating its externally visible key.
	 * @param definition course values
	 * @param clock source of creation time
	 */
	public Course(CourseDefinition definition, Clock clock) {
		validateCourseKey(definition.courseKey());
		validateDates(definition.startsOn(), definition.endsOn());
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.createdAt = now;
		apply(definition, now);
	}

	/**
	 * Replaces course values while preserving its stable key.
	 * @param definition replacement course values
	 * @param clock application clock
	 */
	public void update(CourseDefinition definition, Clock clock) {
		if (!this.courseKey.equals(definition.courseKey())) {
			throw new CourseIdentityMismatchException("courseKey cannot be changed");
		}
		validateCourseKey(definition.courseKey());
		validateDates(definition.startsOn(), definition.endsOn());
		apply(definition, Instant.now(clock));
	}

	/**
	 * Determines whether self-service registration is open at an instant.
	 * @param now instant to evaluate inclusively against configured bounds
	 * @return true when registration is enabled for an active course and inside both
	 * bounds
	 */
	public boolean registrationOpen(Instant now) {
		return this.registrationEnabled && this.status == CourseStatus.ACTIVE
				&& (this.registrationOpensAt == null || !now.isBefore(this.registrationOpensAt))
				&& (this.registrationClosesAt == null || !now.isAfter(this.registrationClosesAt));
	}

	/**
	 * Returns the course identifier.
	 * @return course identifier
	 */
	public UUID id() {
		return this.id;
	}

	/**
	 * Converts this entity to its public read model.
	 * @return course view
	 */
	public CourseView toView() {
		return new CourseView(this.id, this.courseKey, this.name, this.description, this.semester, this.startsOn,
				this.endsOn, this.timezone, this.status, this.registrationOpensAt, this.registrationClosesAt,
				this.registrationEnabled);
	}

	private void apply(CourseDefinition definition, Instant updatedAt) {
		this.courseKey = definition.courseKey();
		this.name = definition.name();
		this.description = definition.description();
		this.semester = definition.semester();
		this.startsOn = definition.startsOn();
		this.endsOn = definition.endsOn();
		this.timezone = definition.timezone();
		this.status = definition.status();
		this.registrationOpensAt = definition.registrationOpensAt();
		this.registrationClosesAt = definition.registrationClosesAt();
		this.registrationEnabled = definition.registrationEnabled();
		this.updatedAt = updatedAt;
	}

	private static void validateCourseKey(String courseKey) {
		if (!COURSE_KEY.matcher(courseKey).matches()) {
			throw new IllegalArgumentException("Course key must be 2-64 lowercase filesystem-safe characters");
		}
	}

	private static void validateDates(@Nullable LocalDate startsOn, @Nullable LocalDate endsOn) {
		if (startsOn != null && endsOn != null && startsOn.isAfter(endsOn)) {
			throw new IllegalArgumentException("Course start date must not be after its end date");
		}
	}

}
