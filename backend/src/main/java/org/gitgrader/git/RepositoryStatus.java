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

package org.gitgrader.git;

/**
 * Lifecycle of a student's assignment repository.
 */
public enum RepositoryStatus {

	/** The row exists but the bare repository has not been created on disk yet. */
	PENDING,

	/** Provisioned and accepting pushes, subject to the assignment schedule. */
	READY,

	/**
	 * Readable but frozen; an instructor locked it, typically during an investigation.
	 */
	LOCKED,

	/** Retained for the record after the course ended; no further pushes. */
	ARCHIVED;

	/**
	 * Whether a push may be accepted into a repository in this state.
	 *
	 * <p>
	 * Only a schedule-independent check. The assignment's own deadline rules are applied
	 * separately and can still refuse a push into a {@code READY} repository.
	 * @return true when the repository itself is not blocking writes
	 */
	public boolean acceptsPushes() {
		return this == READY;
	}

}
