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

package org.gitgrader.assignments;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Provides assignment reads and server-time admission decisions. */
public interface AssignmentCatalog {

	/**
	 * Finds an assignment.
	 * @param id assignment identifier
	 * @return matching assignment, if present
	 */
	Optional<AssignmentView> findAssignment(UUID id);

	/**
	 * Lists assignments for a course.
	 * @param courseId course identifier
	 * @return assignments in display order
	 */
	List<AssignmentView> findByCourse(UUID courseId);

	/**
	 * Lists every assignment in deterministic course and display order.
	 * @return all assignments
	 */
	List<AssignmentView> findAll();

	/**
	 * Returns the student's live extended deadline or the assignment deadline.
	 * @param assignmentId assignment identifier
	 * @param studentId student identifier
	 * @return effective due instant
	 */
	Instant effectiveDueAt(UUID assignmentId, UUID studentId);

	/**
	 * Evaluates admission strictly from the supplied server receive timestamp.
	 * @param assignmentId assignment identifier
	 * @param studentId student identifier
	 * @param serverReceivedAt timestamp assigned by the server receiving the push
	 * @return admission result and lateness
	 */
	AdmissionDecision canAccept(UUID assignmentId, UUID studentId, Instant serverReceivedAt);

}
