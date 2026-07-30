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
 * A submission as other modules and the instructor API see it.
 *
 * <p>
 * Carries no student name, e-mail or student number. Callers that need to display a
 * person resolve them through the {@code identity} module, which keeps this type usable
 * on the public result page without accidentally leaking an identifier into it.
 *
 * @param id the submission
 * @param repositoryId the repository it was pushed to
 * @param repositoryPath where that repository lives on disk
 * @param studentId who pushed
 * @param courseId owning course
 * @param assignmentId the assignment answered
 * @param commitSha the pushed commit
 * @param shortCommitSha an abbreviated commit hash for display
 * @param gitRef the ref that was updated
 * @param commitMessage the commit subject and body
 * @param receivedAt server-side receive time, which is what deadlines were judged on
 * @param signatureStatus the recorded signature outcome
 * @param signatureFingerprint fingerprint of the signing key
 * @param status current grading status
 * @param late whether it arrived after the effective deadline
 * @param effectiveDueAt the deadline that applied, including any extension
 * @param rejectionReason why the push was refused, when it was
 * @param runtimeImageDigest the immutable image the run used
 */
public record SubmissionView(UUID id, UUID repositoryId, @Nullable String repositoryPath, UUID studentId, UUID courseId,
		UUID assignmentId, String commitSha, String shortCommitSha, String gitRef, @Nullable String commitMessage,
		Instant receivedAt, SignatureVerdict signatureStatus, @Nullable String signatureFingerprint,
		SubmissionStatus status, boolean late, @Nullable Instant effectiveDueAt, @Nullable String rejectionReason,
		@Nullable String runtimeImageDigest) {

	/**
	 * Whether the signature badge should read {@code Verified}.
	 * @return true only when the commit was signed by a key registered to this student
	 */
	public boolean signatureVerified() {
		return this.signatureStatus.isAcceptable();
	}

}
