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

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentAdministration;
import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.web.AssignmentController;
import org.gitgrader.identity.ActorProvider;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.identity.StudentView;
import org.gitgrader.submissions.SubmissionSearch;
import org.gitgrader.submissions.SignatureVerdict;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.gitgrader.submissions.SubmissionView;
import org.gitgrader.submissions.web.SubmissionController;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

class ListControllerTest {

	@Test
	void assignmentsListWorksWithAndWithoutCourseId() throws Exception {
		AssignmentCatalog catalog = mock(AssignmentCatalog.class);
		when(catalog.findAll()).thenReturn(List.of());
		when(catalog.findByCourse(any())).thenReturn(List.of());
		MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(
					new AssignmentController(catalog, mock(AssignmentAdministration.class), mock(ActorProvider.class)))
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.build();
		UUID courseId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/assignments")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/assignments").param("courseId", courseId.toString())).andExpect(status().isOk());

		verify(catalog).findAll();
		verify(catalog).findByCourse(courseId);
	}

	@Test
	void submissionsListWorksWithAndWithoutCourseId() throws Exception {
		SubmissionService submissions = mock(SubmissionService.class);
		StudentDirectory students = mock(StudentDirectory.class);
		when(submissions.search(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SubmissionController(submissions, students))
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.build();
		UUID courseId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/submissions")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/submissions").param("courseId", courseId.toString())).andExpect(status().isOk());

		verify(submissions).search(eq(new SubmissionSearch(null, null, null, null)), any());
		verify(submissions).search(eq(new SubmissionSearch(courseId, null, null, null)), any());
	}

	@Test
	void submissionsListIncludesStudentUsername() throws Exception {
		SubmissionService submissions = mock(SubmissionService.class);
		StudentDirectory students = mock(StudentDirectory.class);
		UUID studentId = UUID.randomUUID();
		StudentView student = student(studentId);
		SubmissionView submission = new SubmissionView(UUID.randomUUID(), UUID.randomUUID(), null, studentId,
				UUID.randomUUID(), UUID.randomUUID(), "a".repeat(40), "aaaaaaa", "refs/heads/main", null,
				Instant.parse("2026-03-01T10:00:00Z"), SignatureVerdict.VERIFIED, null, SubmissionStatus.PASSED, false,
				null, null, null);
		when(submissions.search(any(), any()))
			.thenReturn(new PageImpl<>(List.of(submission), PageRequest.of(0, 20), 1));
		when(students.findByIds(any())).thenReturn(List.of(student));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SubmissionController(submissions, students))
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.build();

		mockMvc.perform(get("/api/v1/submissions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.content[0].studentUsername").value("alice"));
		verify(students).findByIds(eq(List.of(studentId)));
	}

	@Test
	void submissionDetailIncludesStudentUsername() throws Exception {
		SubmissionService submissions = mock(SubmissionService.class);
		StudentDirectory students = mock(StudentDirectory.class);
		UUID studentId = UUID.randomUUID();
		UUID submissionId = UUID.randomUUID();
		SubmissionView submission = submission(submissionId, studentId);
		when(submissions.findById(submissionId)).thenReturn(Optional.of(submission));
		when(students.findById(studentId)).thenReturn(Optional.of(student(studentId)));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SubmissionController(submissions, students)).build();

		mockMvc.perform(get("/api/v1/submissions/{id}", submissionId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.studentUsername").value("alice"));
	}

	private static StudentView student(UUID id) {
		return new StudentView(id, "alice", "Alice Example", "alice@example.edu", StudentStatus.SELF_REGISTERED, null,
				Instant.parse("2026-03-01T10:00:00Z"));
	}

	private static SubmissionView submission(UUID id, UUID studentId) {
		return new SubmissionView(id, UUID.randomUUID(), null, studentId, UUID.randomUUID(), UUID.randomUUID(),
				"a".repeat(40), "aaaaaaa", "refs/heads/main", null, Instant.parse("2026-03-01T10:00:00Z"),
				SignatureVerdict.VERIFIED, null, SubmissionStatus.PASSED, false, null, null, null);
	}

	/**
	 * Every filter has to reach the query rather than be applied to a page already read.
	 *
	 * <p>
	 * Filtering after pagination dropped matches that sat on other pages and reported the
	 * matches on the current page as the total, so a filtered list could answer with an
	 * empty first page and a count that contradicted it.
	 * @throws Exception when the request cannot be performed
	 */
	@Test
	void submissionFiltersReachTheQueryRatherThanThePage() throws Exception {
		SubmissionService submissions = mock(SubmissionService.class);
		StudentDirectory students = mock(StudentDirectory.class);
		when(submissions.search(any(), any())).thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SubmissionController(submissions, students))
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.build();
		UUID courseId = UUID.randomUUID();
		UUID assignmentId = UUID.randomUUID();
		UUID studentId = UUID.randomUUID();

		mockMvc
			.perform(get("/api/v1/submissions").param("courseId", courseId.toString())
				.param("assignmentId", assignmentId.toString())
				.param("studentId", studentId.toString())
				.param("status", "PASSED"))
			.andExpect(status().isOk());

		verify(submissions).search(eq(new SubmissionSearch(courseId, assignmentId, studentId, SubmissionStatus.PASSED)),
				any());
	}

}
