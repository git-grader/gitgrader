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

package org.gitgrader.identity;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Published after a student's username changed through a profile edit.
 *
 * <p>
 * The username is baked into every repository path at provisioning time, so a rename that
 * only rewrote {@code students.student_username} used to orphan the student's bare
 * repositories: the rows kept addressing the old spelling and the directories were never
 * moved, leaving the student with "Repository not found" on a URL that looked right. The
 * {@code git} module consumes this event and renames both the rows and the directories.
 *
 * <p>
 * Carries the old and new spellings plus the acting principal, so a listener can rebuild
 * the affected paths and the audit trail can say who corrected the id.
 *
 * @param studentId the student whose username changed
 * @param oldUsername the previous username, as it appears in existing repository paths
 * @param newUsername the new username
 * @param actorType the kind of principal that made the edit, see {@link ActorType}
 * @param actorId stable identifier of the acting principal
 * @param actorDisplayName human-readable label of the acting principal, or {@code null}
 */
public record StudentUsernameChanged(UUID studentId, String oldUsername, String newUsername, String actorType,
		String actorId, @Nullable String actorDisplayName) {
}
