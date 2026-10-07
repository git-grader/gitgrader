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

package org.gitgrader.submissions;

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * Score-independent submission columns needed by aggregate reports.
 *
 * @param submissionId submission identifier
 * @param studentId student who submitted
 * @param assignmentId assignment submitted to
 * @param status admission status of the submission
 * @param commitSha full commit SHA
 * @param gitRef ref the push targeted
 * @param commitMessage commit message, or {@code null} when the push carried none
 * @param receivedAt server-side instant the submission was admitted
 * @param late whether the submission arrived after the applicable deadline
 */
public record SubmissionAssessmentView(UUID submissionId, UUID studentId, UUID assignmentId, SubmissionStatus status,
		String commitSha, String gitRef, @Nullable String commitMessage, Instant receivedAt, boolean late) {
}
