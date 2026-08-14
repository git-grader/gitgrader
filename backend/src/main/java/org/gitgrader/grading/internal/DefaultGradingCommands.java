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

package org.gitgrader.grading.internal;

import java.util.Optional;
import java.util.UUID;

import org.gitgrader.grading.GradingCommands;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionView;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Queues a regrade through the same path an accepted push takes. */
@Service
class DefaultGradingCommands implements GradingCommands {

	private final GradingOrchestrator orchestrator;

	private final SubmissionService submissions;

	DefaultGradingCommands(GradingOrchestrator orchestrator, SubmissionService submissions) {
		this.orchestrator = orchestrator;
		this.submissions = submissions;
	}

	@Override
	@Transactional
	public Optional<UUID> regrade(UUID submissionId) {
		SubmissionView submission = this.submissions.findById(submissionId)
			.orElseThrow(() -> new IllegalArgumentException("Submission not found"));
		return this.orchestrator
			.enqueue(submission.id(), submission.studentId(), submission.courseId(), submission.assignmentId(),
					MANUAL_RETRY)
			.map(GradingRun::id);
	}

}
