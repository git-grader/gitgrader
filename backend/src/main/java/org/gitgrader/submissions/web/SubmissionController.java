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

package org.gitgrader.submissions.web;

import java.util.List;
import java.util.UUID;

import org.gitgrader.submissions.SubmissionSearch;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Serves instructor submission searches and detail. */
@RestController
@RequestMapping("/api/v1/submissions")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class SubmissionController {

	private final SubmissionService submissions;

	public SubmissionController(SubmissionService submissions) {
		this.submissions = submissions;
	}

	@GetMapping
	public Page<SubmissionView> list(@RequestParam(required = false) @Nullable UUID courseId,
			@RequestParam(required = false) @Nullable UUID assignmentId,
			@RequestParam(required = false) @Nullable UUID studentId,
			@RequestParam(required = false) @Nullable SubmissionStatus status, Pageable pageable) {
		Pageable ordered = pageable.getSort().isSorted() ? pageable : PageRequest.of(pageable.getPageNumber(),
				pageable.getPageSize(), Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id")));
		return this.submissions.search(new SubmissionSearch(courseId, assignmentId, studentId, status), ordered);
	}

	@GetMapping("/{id}")
	public InstructorSubmission detail(@PathVariable UUID id) {
		SubmissionView submission = this.submissions.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Submission not found"));
		return new InstructorSubmission(submission, List.of(), null, null);
	}

	/** Instructor-only submission details, including optional logs. */
	public record InstructorSubmission(SubmissionView submission, List<Object> tests, @Nullable String stdout,
			@Nullable String stderr) {
	}

}
