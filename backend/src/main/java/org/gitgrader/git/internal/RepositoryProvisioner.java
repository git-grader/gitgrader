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
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.registration.StudentRegistered;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

	private static final Logger logger = LoggerFactory.getLogger(RepositoryProvisioner.class);

	private final AssignmentCatalog assignments;

	private final GitRepositoryService repositories;

	RepositoryProvisioner(AssignmentCatalog assignments, GitRepositoryService repositories) {
		this.assignments = assignments;
		this.repositories = repositories;
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
					.repositoryPathFor(event.courseKey(), assignment.assignmentKey(), event.studentNumber()),
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
