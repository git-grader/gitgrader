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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DashboardControllerTest {

	@Test
	void dashboardReturnsEveryCounter() throws Exception {
		CourseCatalog courses = mock(CourseCatalog.class);
		StudentDirectory students = mock(StudentDirectory.class);
		AssignmentCatalog assignments = mock(AssignmentCatalog.class);
		SubmissionService submissions = mock(SubmissionService.class);
		when(courses.findCourses(any(), any())).thenReturn(new PageImpl<>(List.of()));
		when(students.search(any(), any())).thenReturn(new PageImpl<>(List.of()));
		when(assignments.findAll()).thenReturn(List.of(assignment(AssignmentStatus.OPEN)));
		when(submissions.countByStatus(SubmissionStatus.RUNNING)).thenReturn(2L);
		when(submissions.countByStatus(SubmissionStatus.INFRASTRUCTURE_ERROR)).thenReturn(3L);
		MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new DashboardController(courses, students, assignments, submissions))
			.build();

		mockMvc.perform(get("/api/v1/dashboard"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.courseCount").exists())
			.andExpect(jsonPath("$.studentCount").exists())
			.andExpect(jsonPath("$.openAssignmentCount").value(1))
			.andExpect(jsonPath("$.runningGradingCount").value(2))
			.andExpect(jsonPath("$.failedInfrastructureCount").value(3))
			.andExpect(jsonPath("$.recentActivity").doesNotExist());
	}

	private static AssignmentView assignment(AssignmentStatus status) {
		return new AssignmentView(UUID.randomUUID(), UUID.randomUUID(), "a", "Assignment", null, 0, status, true,
				Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), "UTC", BigDecimal.TEN, 1,
				BigDecimal.TEN, false, null, null, null, null, null, null, null, false);
	}

}
