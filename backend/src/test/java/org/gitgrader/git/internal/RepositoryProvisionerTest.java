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

package org.gitgrader.git.internal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.registration.StudentRegistered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link RepositoryProvisioner}.
 *
 * <p>
 * Nothing created a student's repositories before this listener existed, so a student
 * could register, have their key accepted, and then be refused on clone. These tests hold
 * the path and the selection rule in place, because both are what a student meets first.
 */
class RepositoryProvisionerTest {

	private static final UUID COURSE = UUID.fromString("00000000-0000-0000-0000-0000000000c1");

	private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

	private static final UUID TEMPLATE = UUID.fromString("00000000-0000-0000-0000-0000000000e1");

	private AssignmentCatalog catalog;

	private GitRepositoryService repositories;

	private RepositoryProvisioner provisioner;

	@BeforeEach
	void setUp() {
		this.catalog = mock(AssignmentCatalog.class);
		this.repositories = mock(GitRepositoryService.class);
		this.provisioner = new RepositoryProvisioner(this.catalog, this.repositories);
	}

	@Test
	@DisplayName("creates a repository at the path the student will clone")
	void provisionsAtTheCloneablePath() {
		when(this.catalog.findByCourse(COURSE)).thenReturn(List.of(assignment("assignment-01", AssignmentStatus.OPEN)));

		this.provisioner.onStudentRegistered(event());

		// The path is the contract with the SSH endpoint: it is what the clone URL
		// contains, so an inconsistency here is only ever discovered by a student.
		verify(this.repositories).provision(any(), eq(STUDENT), eq("example-programming/assignment-01/s1001"),
				eq(TEMPLATE));
	}

	@Test
	@DisplayName("covers work that has not opened yet")
	void provisionsScheduledWork() {
		when(this.catalog.findByCourse(COURSE))
			.thenReturn(List.of(assignment("assignment-02", AssignmentStatus.SCHEDULED)));

		this.provisioner.onStudentRegistered(event());

		// A scheduled assignment is announced coursework. The repository exists ahead of
		// time so the student can clone it; whether a push is accepted is decided
		// separately by the admission rules.
		verify(this.repositories).provision(any(), eq(STUDENT), eq("example-programming/assignment-02/s1001"),
				eq(TEMPLATE));
	}

	@Test
	@DisplayName("ignores drafts and finished work")
	void skipsAssignmentsAStudentCannotHandIn() {
		when(this.catalog.findByCourse(COURSE)).thenReturn(List.of(assignment("draft", AssignmentStatus.DRAFT),
				assignment("closed", AssignmentStatus.CLOSED), assignment("archived", AssignmentStatus.ARCHIVED)));

		this.provisioner.onStudentRegistered(event());

		// A draft is not yet coursework and would leak an instructor's unfinished work.
		verify(this.repositories, never()).provision(any(), any(), any(), any());
	}

	@Test
	@DisplayName("one failing assignment does not cost the student the others")
	void continuesAfterAFailureAndThenReports() {
		when(this.catalog.findByCourse(COURSE)).thenReturn(
				List.of(assignment("broken", AssignmentStatus.OPEN), assignment("healthy", AssignmentStatus.OPEN)));
		when(this.repositories.provision(any(), any(), eq("example-programming/broken/s1001"), any()))
			.thenThrow(new IllegalStateException("disk full"));

		assertThatThrownBy(() -> this.provisioner.onStudentRegistered(event()))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("broken");

		// The healthy one is still created. Raising afterwards leaves the event
		// incomplete so it is retried, and provisioning is idempotent, so the retry
		// only fills the gap.
		verify(this.repositories).provision(any(), eq(STUDENT), eq("example-programming/healthy/s1001"), any());
	}

	private static StudentRegistered event() {
		return new StudentRegistered(STUDENT, "s1001", COURSE, "example-programming",
				Instant.parse("2026-07-30T10:00:00Z"));
	}

	private static AssignmentView assignment(String key, AssignmentStatus status) {
		return new AssignmentView(UUID.randomUUID(), COURSE, key, key, null, 1, status, true, null, null, "UTC",
				new BigDecimal("100"), 10, new BigDecimal("70"), false, TEMPLATE, null, null, null, null, null, null,
				false);
	}

}
