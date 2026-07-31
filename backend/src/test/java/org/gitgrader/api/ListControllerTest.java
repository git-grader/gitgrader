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

import java.util.List;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentAdministration;
import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.web.AssignmentController;
import org.gitgrader.identity.ActorProvider;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.web.SubmissionController;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
		when(submissions.findAll(any())).thenReturn(new PageImpl<>(List.of()));
		when(submissions.findByCourse(any(), any())).thenReturn(new PageImpl<>(List.of()));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new SubmissionController(submissions))
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.build();
		UUID courseId = UUID.randomUUID();

		mockMvc.perform(get("/api/v1/submissions")).andExpect(status().isOk());
		mockMvc.perform(get("/api/v1/submissions").param("courseId", courseId.toString())).andExpect(status().isOk());

		verify(submissions).findAll(any());
		verify(submissions).findByCourse(org.mockito.ArgumentMatchers.eq(courseId), any());
	}

}
