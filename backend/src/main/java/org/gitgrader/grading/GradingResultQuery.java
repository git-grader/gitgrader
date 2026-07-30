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

package org.gitgrader.grading;

import java.util.Optional;
import java.util.UUID;

/**
 * Reads grading outcomes on behalf of the people they belong to.
 */
public interface GradingResultQuery {

	/**
	 * Finds the outcome of the most recent attempt at a submission.
	 *
	 * <p>
	 * The latest attempt is the one that counts: a run may be retried after an
	 * infrastructure failure, and the student should see the answer that stands rather
	 * than the first one recorded.
	 * @param submissionId the submission
	 * @return the redacted outcome, or empty when nothing has been graded yet
	 */
	Optional<StudentGradingResult> findLatestForSubmission(UUID submissionId);

}
