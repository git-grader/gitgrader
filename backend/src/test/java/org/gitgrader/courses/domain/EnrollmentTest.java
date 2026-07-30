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
import java.util.UUID;

import org.gitgrader.courses.CourseDefinition;
import org.gitgrader.courses.CourseStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EnrollmentTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);

	@Test
	void classMustBelongToEnrollmentCourse() {
		Course first = course("java-101");
		Course second = course("java-102");
		CourseClass otherCourseClass = new CourseClass(second.id(), "b", "Class B", CLOCK);

		assertThatThrownBy(() -> new Enrollment(UUID.randomUUID(), first, otherCourseClass, CLOCK))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("does not belong");
	}

	private static Course course(String key) {
		return new Course(
				new CourseDefinition(key, "Java", null, null, null, null, "UTC", CourseStatus.ACTIVE, null, null, true),
				CLOCK);
	}

}
