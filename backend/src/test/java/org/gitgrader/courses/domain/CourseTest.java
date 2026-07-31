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
import java.time.ZoneOffset;

import org.gitgrader.courses.CourseDefinition;
import org.gitgrader.courses.CourseStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);

	@Test
	void registrationWindowIsInclusiveAndNullBoundsAreUnbounded() {
		Instant opens = Instant.parse("2026-03-01T00:00:00Z");
		Instant closes = Instant.parse("2026-03-31T23:59:59Z");
		Course bounded = course("java-101", CourseStatus.ACTIVE, true, opens, closes);
		Course unbounded = course("java-102", CourseStatus.ACTIVE, true, null, null);

		assertThat(bounded.registrationOpen(opens)).isTrue();
		assertThat(bounded.registrationOpen(closes)).isTrue();
		assertThat(unbounded.registrationOpen(Instant.parse("2050-01-01T00:00:00Z"))).isTrue();
	}

	@Test
	void closedWindowsDisabledRegistrationAndInactiveCoursesRejectRegistration() {
		Instant opens = Instant.parse("2026-03-10T00:00:00Z");
		Instant closes = Instant.parse("2026-03-20T00:00:00Z");

		assertThat(course("java-101", CourseStatus.ACTIVE, true, opens, closes)
			.registrationOpen(Instant.parse("2026-03-09T23:59:59Z"))).isFalse();
		assertThat(course("java-102", CourseStatus.ACTIVE, true, opens, closes)
			.registrationOpen(Instant.parse("2026-03-20T00:00:01Z"))).isFalse();
		assertThat(course("java-103", CourseStatus.ACTIVE, false, null, null).registrationOpen(Instant.now(CLOCK)))
			.isFalse();
		assertThat(course("java-104", CourseStatus.DRAFT, true, null, null).registrationOpen(Instant.now(CLOCK)))
			.isFalse();
	}

	@Test
	void invalidFilesystemCourseKeysAreRejected() {
		assertThatThrownBy(() -> course("../unsafe", CourseStatus.ACTIVE, true, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Course key");
		assertThatThrownBy(() -> course("A", CourseStatus.ACTIVE, true, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Course key");
	}

	@Test
	void updatingToActiveAndEnablingRegistrationOpensRegistration() {
		Course course = course("java-101", CourseStatus.DRAFT, false, null, null);
		CourseDefinition active = new CourseDefinition("java-101", "Updated Java", "Description", "Spring", null, null,
				"Europe/Zurich", CourseStatus.ACTIVE, null, null, true);

		course.update(active, CLOCK);

		assertThat(course.registrationOpen(Instant.now(CLOCK))).isTrue();
	}

	private static Course course(String key, CourseStatus status, boolean enabled, Instant opens, Instant closes) {
		return new Course(
				new CourseDefinition(key, "Java", null, null, null, null, "UTC", status, opens, closes, enabled),
				CLOCK);
	}

}
