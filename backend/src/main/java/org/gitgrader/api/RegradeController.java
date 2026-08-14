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

package org.gitgrader.api;

import java.util.Optional;
import java.util.UUID;

import org.gitgrader.grading.GradingCommands;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Starts a fresh grading run for a submission that has already been recorded.
 *
 * <p>
 * Lives here rather than beside the other submission endpoints because queueing work is
 * the grading module's to do, and the submissions module cannot depend on grading without
 * closing a dependency cycle. This module already sits above both.
 */
@RestController
@RequestMapping("/api/v1/submissions")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class RegradeController {

	private final GradingCommands grading;

	public RegradeController(GradingCommands grading) {
		this.grading = grading;
	}

	/**
	 * Queues a regrade.
	 *
	 * <p>
	 * Answers 202 with the run that was queued. A queue ceiling refusing the request is
	 * reported as 429 rather than as a silent success, because the caller pressed a
	 * button and is entitled to know that nothing will happen.
	 * @param id the submission to grade again
	 * @return the queued run's identifier
	 */
	@PostMapping("/{id}/regrade")
	@org.springframework.web.bind.annotation.ResponseStatus(HttpStatus.ACCEPTED)
	public RegradeAccepted regrade(@PathVariable UUID id) {
		Optional<UUID> run = this.grading.regrade(id);
		return new RegradeAccepted(run.orElseThrow(() -> new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
				"The grading queue is full for this student or course. Try again shortly.")));
	}

	/**
	 * The run a regrade started.
	 *
	 * @param gradingRunId the queued run
	 */
	public record RegradeAccepted(UUID gradingRunId) {
	}

}
