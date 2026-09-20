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
import java.util.Optional;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentPublished;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.git.domain.RepositoryRecord;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentView;
import org.gitgrader.registration.StudentRegistered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Creates the repositories a newly registered student pushes to.
 *
 * <p>
 * Registration and provisioning are separated by an event rather than a direct call: a
 * student is registered whether or not their repositories could be created, and creating
 * a dozen repositories on disk should not sit inside the request that answers the
 * registration form.
 */
@Component
class RepositoryProvisioner {

	private static final int REPOSITORY_PATH_SEGMENT_COUNT = 3;

	private static final int GIT_SUFFIX_LENGTH = 4;

	private static final Logger logger = LoggerFactory.getLogger(RepositoryProvisioner.class);

	private final AssignmentCatalog assignments;

	private final GitRepositoryService repositories;

	private final CourseCatalog courses;

	private final StudentDirectory students;

	RepositoryProvisioner(AssignmentCatalog assignments, GitRepositoryService repositories) {
		this(assignments, repositories, null, null);
	}

	@Autowired
	RepositoryProvisioner(AssignmentCatalog assignments, GitRepositoryService repositories, CourseCatalog courses,
			StudentDirectory students) {
		this.assignments = assignments;
		this.repositories = repositories;
		this.courses = courses;
		this.students = students;
	}

	Optional<RepositoryRecord> resolveOrProvision(String requestedPath,
			StudentKeyAuthenticator.AuthenticatedStudent student) {
		Optional<RepositoryRecord> existing = this.repositories.resolve(requestedPath);
		if (existing.isPresent()) {
			return existing.filter((record) -> record.studentId().equals(student.studentId()));
		}
		String path = requestedPath.startsWith("/") ? requestedPath.substring(1) : requestedPath;
		if (path.endsWith(".git")) {
			path = path.substring(0, path.length() - GIT_SUFFIX_LENGTH);
		}
		String[] segments = path.split("/");
		if (segments.length != REPOSITORY_PATH_SEGMENT_COUNT || !segments[2].equals(studentUsername(student))) {
			return Optional.empty();
		}
		var course = this.courses.findCourseByKey(segments[0]);
		if (course.isEmpty() || !this.courses.findEnrolledStudentIds(course.get().id()).contains(student.studentId())) {
			return Optional.empty();
		}
		AssignmentView assignment = this.assignments.findByCourse(course.get().id())
			.stream()
			.filter((candidate) -> candidate.assignmentKey().equals(segments[1]))
			.filter((candidate) -> worthProvisioning(candidate.status()))
			.findFirst()
			.orElse(null);
		if (assignment == null) {
			return Optional.empty();
		}
		StudentView profile = this.students.findById(student.studentId()).orElseThrow();
		return Optional.of(this.repositories.provision(assignment.id(), student.studentId(), GitRepositoryService
			.repositoryPathFor(course.get().courseKey(), assignment.assignmentKey(), profile.studentUsername()),
				assignment.templateVersionId()));
	}

	private String studentUsername(StudentKeyAuthenticator.AuthenticatedStudent student) {
		return this.students.findById(student.studentId()).map((profile) -> profile.studentUsername()).orElse("");
	}

	@ApplicationModuleListener
	void onAssignmentPublished(AssignmentPublished event) {
		var course = this.courses.findCourse(event.courseId()).orElseThrow();
		AssignmentView assignment = this.assignments.findAssignment(event.assignmentId()).orElseThrow();
		List<String> failures = new ArrayList<>();
		int created = 0;
		for (var student : this.students.findByIds(this.courses.findEnrolledStudentIds(event.courseId()))) {
			try {
				this.repositories.provision(event.assignmentId(), student.id(), GitRepositoryService
					.repositoryPathFor(course.courseKey(), event.assignmentKey(), student.studentUsername()),
						assignment.templateVersionId());
				created++;
			}
			catch (RuntimeException exception) {
				logger.error("Could not provision assignment {} for student {}", event.assignmentKey(), student.id(),
						exception);
				failures.add(student.studentUsername());
			}
		}
		logger.info("Provisioned {} repositories for published assignment {}", created, event.assignmentKey());
		if (!failures.isEmpty()) {
			throw new IllegalStateException(
					"Could not provision repositories for students: " + String.join(", ", failures));
		}
	}

	@ApplicationModuleListener
	void onStudentRegistered(StudentRegistered event) {
		List<String> failures = new ArrayList<>();
		int created = 0;
		for (AssignmentView assignment : this.assignments.findByCourse(event.courseId())) {
			if (!worthProvisioning(assignment.status())) {
				continue;
			}
			try {
				this.repositories.provision(assignment.id(), event.studentId(), GitRepositoryService
					.repositoryPathFor(event.courseKey(), assignment.assignmentKey(), event.studentUsername()),
						assignment.templateVersionId());
				created++;
			}
			catch (RuntimeException ex) {
				// One unusable assignment must not cost the student every other
				// repository, so the loop finishes and the failure is raised afterwards.
				logger.error("Could not provision repository for assignment {} and student {}",
						assignment.assignmentKey(), event.studentId(), ex);
				failures.add(assignment.assignmentKey());
			}
		}
		logger.info("Provisioned {} repositories for student {} on course {}", created, event.studentId(),
				event.courseKey());
		if (!failures.isEmpty()) {
			// Raised so the event is recorded as incomplete and retried. Provisioning
			// returns the existing repository when there is one, so a replay recreates
			// nothing and only retries what is still missing.
			throw new IllegalStateException(
					"Could not provision repositories for assignments: " + String.join(", ", failures));
		}
	}

	/**
	 * Whether a student joining now should get a repository for this assignment.
	 * @param status the assignment's status
	 * @return true for work the student can still be expected to hand in
	 */
	private static boolean worthProvisioning(AssignmentStatus status) {
		return status == AssignmentStatus.OPEN || status == AssignmentStatus.SCHEDULED;
	}

}
