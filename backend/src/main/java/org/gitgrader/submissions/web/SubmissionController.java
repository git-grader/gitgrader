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

import java.util.UUID;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.persistence.EntityNotFoundException;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentView;
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

	private final StudentDirectory students;

	public SubmissionController(SubmissionService submissions, StudentDirectory students) {
		this.submissions = submissions;
		this.students = students;
	}

	@GetMapping
	public Page<InstructorSubmission> list(@RequestParam(required = false) @Nullable UUID courseId,
			@RequestParam(required = false) @Nullable UUID assignmentId,
			@RequestParam(required = false) @Nullable UUID studentId,
			@RequestParam(required = false) @Nullable SubmissionStatus status, Pageable pageable) {
		Pageable ordered = pageable.getSort().isSorted() ? pageable : PageRequest.of(pageable.getPageNumber(),
				pageable.getPageSize(), Sort.by(Sort.Order.desc("receivedAt"), Sort.Order.desc("id")));
		Page<SubmissionView> page = this.submissions
			.search(new SubmissionSearch(courseId, assignmentId, studentId, status), ordered);
		Map<UUID, String> usernames = this.students
			.findByIds(page.getContent().stream().map(SubmissionView::studentId).distinct().toList())
			.stream()
			.collect(Collectors.toMap(StudentView::id, StudentView::studentUsername));
		return page.map(submission -> new InstructorSubmission(submission, usernames.get(submission.studentId())));
	}

	@GetMapping("/{id}")
	public InstructorSubmission detail(@PathVariable UUID id) {
		SubmissionView submission = this.submissions.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Submission not found"));
		String username = this.students.findById(submission.studentId()).map(StudentView::studentUsername).orElse(null);
		return new InstructorSubmission(submission, username);
	}

	private record InstructorSubmission(@JsonUnwrapped SubmissionView submission, @Nullable String studentUsername) {
	}

}
