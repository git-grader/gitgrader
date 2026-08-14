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

package org.gitgrader.assignments.internal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.domain.DeadlineExtension;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persists deadline extensions including revoked history. */
interface DeadlineExtensionRepository extends JpaRepository<DeadlineExtension, UUID> {

	/**
	 * Lists every extension ever granted on an assignment, newest first.
	 * @param assignmentId assignment identifier
	 * @return granted extensions, revoked ones included
	 */
	List<DeadlineExtension> findByAssignmentIdOrderByGrantedAtDesc(UUID assignmentId);

	/**
	 * Finds the sole live extension for a student and assignment.
	 * @param assignmentId assignment identifier
	 * @param studentId student identifier
	 * @return live extension, if one exists
	 */
	Optional<DeadlineExtension> findByAssignmentIdAndStudentIdAndRevokedAtIsNull(UUID assignmentId, UUID studentId);

	/**
	 * Reports whether the Java-enforced live-extension slot is occupied.
	 * @param assignmentId assignment identifier
	 * @param studentId student identifier
	 * @return true when a non-revoked extension exists
	 */
	boolean existsByAssignmentIdAndStudentIdAndRevokedAtIsNull(UUID assignmentId, UUID studentId);

}
