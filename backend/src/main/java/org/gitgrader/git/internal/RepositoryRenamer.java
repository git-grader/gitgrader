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

import java.util.ArrayList;
import java.util.List;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditService;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseView;
import org.gitgrader.git.domain.RepositoryRecord;
import org.gitgrader.identity.StudentUsernameChanged;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Renames a student's repositories when their username changes.
 *
 * <p>
 * The username is the last segment of every repository path, so a profile correction that
 * only rewrote {@code students.student_username} used to leave the rows and the bare
 * directories under the old spelling, and the student could no longer reach their own
 * repositories. The {@code identity} module publishes {@link StudentUsernameChanged}
 * after the profile row is committed; this listener then moves each bare directory on the
 * git volume and updates its row, so the SSH path lookup resolves under the new spelling
 * again.
 */
@Component
class RepositoryRenamer {

	private static final Logger logger = LoggerFactory.getLogger(RepositoryRenamer.class);

	private final RepositoryRecordRepository records;

	private final AssignmentCatalog assignments;

	private final CourseCatalog courses;

	private final GitRepositoryService repositories;

	private final AuditService auditService;

	RepositoryRenamer(RepositoryRecordRepository records, AssignmentCatalog assignments, CourseCatalog courses,
			GitRepositoryService repositories, AuditService auditService) {
		this.records = records;
		this.assignments = assignments;
		this.courses = courses;
		this.repositories = repositories;
		this.auditService = auditService;
	}

	@ApplicationModuleListener
	void onStudentUsernameChanged(StudentUsernameChanged event) {
		List<RepositoryRecord> owned = this.records.findByStudentId(event.studentId());
		List<String> failures = new ArrayList<>();
		int renamed = 0;
		for (RepositoryRecord record : owned) {
			try {
				AssignmentView assignment = this.assignments.findAssignment(record.assignmentId())
					.orElseThrow(() -> new IllegalStateException("Assignment not found: " + record.assignmentId()));
				CourseView course = this.courses.findCourse(assignment.courseId())
					.orElseThrow(() -> new IllegalStateException("Course not found: " + assignment.courseId()));
				// repositoryPathFor validates the new username as a safe path segment, so
				// an
				// unusable value fails here before anything on disk is touched.
				String newPath = GitRepositoryService.repositoryPathFor(course.courseKey(), assignment.assignmentKey(),
						event.newUsername());
				this.repositories.rename(record, newPath);
				renamed++;
			}
			catch (RuntimeException exception) {
				logger.error("Could not rename repository {} for student {}", record.repositoryPath(),
						event.studentId(), exception);
				failures.add(record.repositoryPath());
			}
		}
		this.auditService.record(AuditRecord.of(AuditEventType.STUDENT_USERNAME_CHANGED)
			.actor(actorType(event.actorType()), event.actorId(), event.actorDisplayName())
			.subject("Student", event.studentId().toString())
			.with("oldUsername", event.oldUsername())
			.with("newUsername", event.newUsername())
			.with("repositoriesRenamed", renamed)
			.build());
		logger.info("Renamed {} repositories of student {} from '{}' to '{}'", renamed, event.studentId(),
				event.oldUsername(), event.newUsername());
		if (!failures.isEmpty()) {
			throw new IllegalStateException("Could not rename repositories: " + String.join(", ", failures));
		}
	}

	private static AuditRecord.ActorType actorType(String name) {
		try {
			return AuditRecord.ActorType.valueOf(name);
		}
		catch (IllegalArgumentException ex) {
			// An unknown actor kind must not fail the rename itself.
			return AuditRecord.ActorType.SYSTEM;
		}
	}

}
