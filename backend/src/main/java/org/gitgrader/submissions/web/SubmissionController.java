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

import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
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
		Pageable ordered = pageable.getSort().isSorted() ? pageable
				: org.springframework.data.domain.PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(),
						org.springframework.data.domain.Sort.by(
								org.springframework.data.domain.Sort.Order.desc("receivedAt"),
								org.springframework.data.domain.Sort.Order.desc("id")));
		Page<SubmissionView> page = courseId == null ? this.submissions.findAll(ordered)
				: this.submissions.findByCourse(courseId, ordered);
		List<SubmissionView> filtered = page.getContent()
			.stream()
			.filter((item) -> assignmentId == null || item.assignmentId().equals(assignmentId))
			.filter((item) -> studentId == null || item.studentId().equals(studentId))
			.filter((item) -> status == null || item.status() == status)
			.toList();
		return new PageImpl<>(filtered, pageable, filtered.size());
	}

	@GetMapping("/{id}")
	public InstructorSubmission detail(@PathVariable UUID id) {
		SubmissionView submission = this.submissions.findById(id)
			.orElseThrow(() -> new IllegalArgumentException("Submission not found"));
		return new InstructorSubmission(submission, List.of(), null, null);
	}

	@PostMapping("/{id}/regrade")
	@ResponseStatus(HttpStatus.ACCEPTED)
	public void regrade(@PathVariable UUID id) {
		if (this.submissions.findById(id).isEmpty()) {
			throw new IllegalArgumentException("Submission not found");
		}
	}

	/** Instructor-only submission details, including optional logs. */
	public record InstructorSubmission(SubmissionView submission, List<Object> tests, @Nullable String stdout,
			@Nullable String stderr) {
	}

}
