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

import org.jspecify.annotations.Nullable;

/**
 * A student's deadline extension and its soft-revocation history.
 *
 * @param id extension identifier
 * @param assignmentId assignment identifier
 * @param studentId student identifier
 * @param extendedDueAt replacement due instant
 * @param reason instructor reason
 * @param grantedBy granting instructor
 * @param grantedAt grant timestamp
 * @param revokedAt revocation timestamp
 * @param revokedBy revoking instructor
 * @param createdAt creation timestamp
 */
public record DeadlineExtensionView(UUID id, UUID assignmentId, UUID studentId, Instant extendedDueAt, String reason,
		String grantedBy, Instant grantedAt, @Nullable Instant revokedAt, @Nullable String revokedBy,
		Instant createdAt) {
}
