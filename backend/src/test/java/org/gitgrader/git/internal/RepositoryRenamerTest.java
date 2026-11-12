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
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditService;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.gitgrader.git.domain.RepositoryRecord;
import org.gitgrader.identity.StudentUsernameChanged;
import org.gitgrader.templates.TemplateCatalog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers the rename cascade that used to be missing: when an instructor corrects a
 * student's username, the repository rows and the bare directories on the git volume must
 * follow, or the student loses access to their own repositories.
 */
class RepositoryRenamerTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-04-01T10:00:00Z"), ZoneOffset.UTC);

	private static final UUID STUDENT = UUID.randomUUID();

	private static final UUID COURSE = UUID.randomUUID();

	private static final UUID ASSIGNMENT = UUID.randomUUID();

	private static final String OLD_PATH = "cs101/assignment-01/s1000042";

	private static final String NEW_PATH = "cs101/assignment-01/s1000043";

	@TempDir
	Path tempDir;

	private RepositoryRecordRepository records;

	private AuditService auditService;

	private RepositoryRenamer renamer;

	private Path root;

	@BeforeEach
	void setUp() {
		this.root = this.tempDir.resolve("repositories");
		this.records = mock(RepositoryRecordRepository.class);
		when(this.records.save(any())).thenAnswer((invocation) -> invocation.getArgument(0));
		when(this.records.findByRepositoryPath(any())).thenReturn(Optional.empty());
		StorageProperties storage = new StorageProperties(this.root.toString(),
				this.tempDir.resolve("templates").toString(), this.tempDir.resolve("tests").toString(),
				this.tempDir.resolve("artifacts").toString(), this.tempDir.resolve("tmp").toString());
		GitRepositoryService repositories = new GitRepositoryService(this.records, mock(TemplateCatalog.class), storage,
				CLOCK);
		AssignmentCatalog assignments = mock(AssignmentCatalog.class);
		when(assignments.findAssignment(ASSIGNMENT)).thenReturn(Optional.of(new AssignmentView(ASSIGNMENT, COURSE,
				"assignment-01", "Assignment 01", null, 1, AssignmentStatus.OPEN, true, null, null, null,
				BigDecimal.TEN, 1, null, false, null, null, null, null, null, null, null, false, false)));
		CourseCatalog courses = mock(CourseCatalog.class);
		when(courses.findCourse(COURSE)).thenReturn(Optional.of(new CourseView(COURSE, "cs101", "Intro to CS", null,
				null, null, null, "UTC", CourseStatus.ACTIVE, null, null, true)));
		this.auditService = mock(AuditService.class);
		this.renamer = new RepositoryRenamer(this.records, assignments, courses, repositories, this.auditService);
	}

	@Test
	@DisplayName("renames the repository row and the bare directory and records the audit event")
	void renamesRowAndDirectory() throws Exception {
		RepositoryRecord record = new RepositoryRecord(ASSIGNMENT, STUDENT, OLD_PATH, CLOCK);
		when(this.records.findByStudentId(STUDENT)).thenReturn(List.of(record));
		Path oldBare = this.root.resolve(OLD_PATH + ".git");
		Files.createDirectories(oldBare);
		Files.writeString(oldBare.resolve("HEAD"), "ref: refs/heads/main\n");

		this.renamer.onStudentUsernameChanged(event("s1000042", "s1000043"));

		assertThat(record.repositoryPath()).isEqualTo(NEW_PATH);
		assertThat(this.root.resolve(OLD_PATH + ".git")).doesNotExist();
		assertThat(this.root.resolve(NEW_PATH + ".git")).isDirectory();
		assertThat(this.root.resolve(NEW_PATH + ".git/HEAD")).hasContent("ref: refs/heads/main\n");
		ArgumentCaptor<AuditRecord> audit = ArgumentCaptor.forClass(AuditRecord.class);
		verify(this.auditService).record(audit.capture());
		assertThat(audit.getValue().type()).isEqualTo(AuditEventType.STUDENT_USERNAME_CHANGED);
	}

	@Test
	@DisplayName("renames the row even when the directory is missing (adoption case)")
	void renamesRowWithoutDirectory() {
		RepositoryRecord record = new RepositoryRecord(ASSIGNMENT, STUDENT, OLD_PATH, CLOCK);
		when(this.records.findByStudentId(STUDENT)).thenReturn(List.of(record));

		this.renamer.onStudentUsernameChanged(event("s1000042", "s1000043"));

		assertThat(record.repositoryPath()).isEqualTo(NEW_PATH);
	}

	@Test
	@DisplayName("fails the event when the target path is already taken by another repository")
	void refusesCollidingTargetPath() {
		RepositoryRecord record = new RepositoryRecord(ASSIGNMENT, STUDENT, OLD_PATH, CLOCK);
		RepositoryRecord other = new RepositoryRecord(UUID.randomUUID(), UUID.randomUUID(), NEW_PATH, CLOCK);
		when(this.records.findByStudentId(STUDENT)).thenReturn(List.of(record));
		when(this.records.findByRepositoryPath(NEW_PATH)).thenReturn(Optional.of(other));

		assertThatThrownBy(() -> this.renamer.onStudentUsernameChanged(event("s1000042", "s1000043")))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining(OLD_PATH);

		assertThat(record.repositoryPath()).isEqualTo(OLD_PATH);
	}

	@Test
	@DisplayName("rejects an unsafe new username before touching disk")
	void refusesUnsafeUsername() {
		RepositoryRecord record = new RepositoryRecord(ASSIGNMENT, STUDENT, OLD_PATH, CLOCK);
		when(this.records.findByStudentId(STUDENT)).thenReturn(List.of(record));

		assertThatThrownBy(() -> this.renamer.onStudentUsernameChanged(event("s1000042", "../victim")))
			.isInstanceOf(IllegalStateException.class);

		assertThat(record.repositoryPath()).isEqualTo(OLD_PATH);
	}

	private static StudentUsernameChanged event(String oldUsername, String newUsername) {
		return new StudentUsernameChanged(STUDENT, oldUsername, newUsername, "INSTRUCTOR", "t.teacher", "Test Teacher");
	}

}
