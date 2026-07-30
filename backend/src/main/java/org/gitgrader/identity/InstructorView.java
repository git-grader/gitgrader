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

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Public read model of a directory-backed instructor.
 *
 * @param id stable local identifier
 * @param username directory username
 * @param displayName directory display name
 * @param email directory email address
 * @param roles comma-separated application roles
 * @param firstLoginAt first observed login time
 * @param lastLoginAt most recent login time
 */
public record InstructorView(UUID id, String username, String displayName, @Nullable String email, String roles,
		Instant firstLoginAt, Instant lastLoginAt) {
}
