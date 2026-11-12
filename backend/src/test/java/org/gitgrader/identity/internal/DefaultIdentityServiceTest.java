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

package org.gitgrader.identity.internal;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.gitgrader.identity.Actor;
import org.gitgrader.identity.ActorType;
import org.gitgrader.identity.StudentRegistration;
import org.gitgrader.identity.StudentUpdate;
import org.gitgrader.identity.StudentUsernameChanged;
import org.gitgrader.identity.domain.Student;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the rename half of a profile edit: a username change must be announced as
 * {@link StudentUsernameChanged} so the git module can move the repositories, and it must
 * refuse values that would collide with another profile or break path safety.
 */
class DefaultIdentityServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC);

	private static final Actor INSTRUCTOR = new Actor(ActorType.INSTRUCTOR, "t.teacher", "Test Teacher");

	private StudentRepository students;

	private ApplicationEventPublisher events;

	private DefaultIdentityService service;

	private Student student;

	@BeforeEach
	void setUp() {
		this.students = mock(StudentRepository.class);
		this.events = mock(ApplicationEventPublisher.class);
		this.service = new DefaultIdentityService(this.students, mock(InstructorRepository.class), CLOCK, this.events);
		this.student = new Student(new StudentRegistration("s1000042", "Max", "Muster", "max@example.org", null, null),
				CLOCK);
		when(this.students.findById(this.student.id())).thenReturn(Optional.of(this.student));
		when(this.students.findByStudentUsernameIgnoreCase(any())).thenReturn(Optional.empty());
	}

	@Test
	@DisplayName("publishes StudentUsernameChanged when the username actually changes")
	void publishesRenameEvent() {
		StudentUpdate update = new StudentUpdate("s1000043", "Max", "Muster", "max@example.org");

		this.service.update(this.student.id(), update, INSTRUCTOR);

		verify(this.events).publishEvent(new StudentUsernameChanged(this.student.id(), "s1000042", "s1000043",
				"INSTRUCTOR", "t.teacher", "Test Teacher"));
		assertThat(this.student.studentUsername()).isEqualTo("s1000043");
	}

	@Test
	@DisplayName("publishes no event when only other fields change")
	void publishesNoEventWhenUsernameUnchanged() {
		StudentUpdate update = new StudentUpdate("s1000042", "Moritz", "Muster", "max@example.org");

		this.service.update(this.student.id(), update, INSTRUCTOR);

		verify(this.events, never()).publishEvent(any());
		assertThat(this.student.studentUsername()).isEqualTo("s1000042");
	}

	@Test
	@DisplayName("refuses a username another profile already uses, even with different case")
	void refusesDuplicateUsername() {
		Student other = new Student(
				new StudentRegistration("S1000043", "Erika", "Muster", "erika@example.org", null, null), CLOCK);
		when(this.students.findByStudentUsernameIgnoreCase("s1000043")).thenReturn(Optional.of(other));
		StudentUpdate update = new StudentUpdate("s1000043", "Max", "Muster", "max@example.org");

		assertThatThrownBy(() -> this.service.update(this.student.id(), update, INSTRUCTOR))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("s1000043");

		verify(this.events, never()).publishEvent(any());
		assertThat(this.student.studentUsername()).isEqualTo("s1000042");
	}

	@Test
	@DisplayName("accepts keeping the same username under a different case spelling of itself")
	void allowsOwnUsername() {
		when(this.students.findByStudentUsernameIgnoreCase("S1000042")).thenReturn(Optional.of(this.student));
		StudentUpdate update = new StudentUpdate("S1000042", "Max", "Muster", "max@example.org");

		this.service.update(this.student.id(), update, INSTRUCTOR);

		assertThat(this.student.studentUsername()).isEqualTo("S1000042");
	}

	@Test
	@DisplayName("refuses a username that is not a safe path segment")
	void refusesUnsafeUsername() {
		StudentUpdate update = new StudentUpdate("../victim", "Max", "Muster", "max@example.org");

		assertThatThrownBy(() -> this.service.update(this.student.id(), update, INSTRUCTOR))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("username");

		verify(this.events, never()).publishEvent(any());
		assertThat(this.student.studentUsername()).isEqualTo("s1000042");
	}

	@Test
	@DisplayName("refuses the dot-directory usernames")
	void refusesDotUsernames() {
		assertThatThrownBy(() -> this.service.update(this.student.id(),
				new StudentUpdate("..", "Max", "Muster", "max@example.org"), INSTRUCTOR))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.service.update(this.student.id(),
				new StudentUpdate(".", "Max", "Muster", "max@example.org"), INSTRUCTOR))
			.isInstanceOf(IllegalArgumentException.class);
	}

}
