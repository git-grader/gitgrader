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
import java.util.UUID;

/** Creates, publishes, and extends assignments. */
public interface AssignmentAdministration {

	/**
	 * Creates an assignment.
	 * @param definition assignment values
	 * @return created assignment
	 */
	AssignmentView create(AssignmentDefinition definition);

	/**
	 * Changes assignment lifecycle status after checking publication completeness.
	 * @param assignmentId assignment identifier
	 * @param status target status
	 * @return updated assignment
	 */
	AssignmentView changeStatus(UUID assignmentId, AssignmentStatus status);

	/**
	 * Grants one live extension to a student.
	 * @param assignmentId assignment identifier
	 * @param studentId student identifier
	 * @param extendedDueAt replacement due instant
	 * @param reason non-blank reason
	 * @param actor granting instructor
	 * @return granted extension
	 */
	DeadlineExtensionView grantExtension(UUID assignmentId, UUID studentId, Instant extendedDueAt, String reason,
			String actor);

	/**
	 * Soft-revokes an extension.
	 * @param extensionId extension identifier
	 * @param actor revoking instructor
	 * @return revoked extension
	 */
	DeadlineExtensionView revokeExtension(UUID extensionId, String actor);

}
