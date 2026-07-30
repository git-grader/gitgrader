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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.git.domain.RepositoryRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for student assignment repositories.
 */
public interface RepositoryRecordRepository extends JpaRepository<RepositoryRecord, UUID> {

	/**
	 * Resolves a repository by the exact path an SSH client asked for.
	 *
	 * <p>
	 * This is the authorization lookup for the whole Git transport. It matches on the
	 * stored path rather than parsing the requested one, so a crafted path cannot be made
	 * to resolve to another student's repository.
	 * @param repositoryPath path relative to the repository root, without the
	 * {@code .git} suffix
	 * @return the repository when one is registered at that exact path
	 */
	Optional<RepositoryRecord> findByRepositoryPath(String repositoryPath);

	/**
	 * Finds the repository a student holds for one assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return the repository, if it has been created
	 */
	Optional<RepositoryRecord> findByStudentIdAndAssignmentId(UUID studentId, UUID assignmentId);

	/**
	 * Lists every repository a student holds.
	 * @param studentId the student
	 * @return the student's repositories
	 */
	List<RepositoryRecord> findByStudentId(UUID studentId);

}
