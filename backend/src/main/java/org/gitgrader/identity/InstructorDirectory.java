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

import java.util.Set;

import org.jspecify.annotations.Nullable;

/** Maintains the local projection of directory-backed instructors. */
public interface InstructorDirectory {

	/**
	 * Creates or refreshes an instructor when a directory login succeeds.
	 * @param username directory username
	 * @param displayName directory display name
	 * @param email directory email address
	 * @param roles application roles
	 * @return current local projection
	 */
	InstructorView upsertOnLogin(String username, String displayName, @Nullable String email, Set<String> roles);

}
