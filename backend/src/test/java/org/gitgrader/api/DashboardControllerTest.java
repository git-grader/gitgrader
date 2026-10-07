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
import java.util.Map;
import java.util.UUID;

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests for {@link DashboardController}.
 *
 * <p>
 * The summary is assembled from four independent sources, and each counter has to survive
 * that trip with its own value rather than being folded into a total that happens to look
 * right. The absence of {@code recentActivity} is asserted too: it was declared, always
 * empty, and a field that promises activity and never carries any is worse than none.
 */
class DashboardControllerTest {

	@Test
	@DisplayName("returns every counter the dashboard reports")
	void dashboardReturnsEveryCounter() throws Exception {
		CourseCatalog courses = mock(CourseCatalog.class);
		StudentDirectory students = mock(StudentDirectory.class);
		AssignmentCatalog assignments = mock(AssignmentCatalog.class);
		SubmissionService submissions = mock(SubmissionService.class);
		// The course total is summed across every status, so each one reports a different
		// number: dropping a status or counting only one changes the result. The five
		// counters deliberately share no value, so wiring any pair of them together is
		// visible instead of passing as an accidental zero.
		Map<CourseStatus, Long> coursesPerStatus = Map.of(CourseStatus.DRAFT, 1L, CourseStatus.ACTIVE, 2L,
				CourseStatus.CLOSED, 3L, CourseStatus.ARCHIVED, 4L);
		for (CourseStatus status : CourseStatus.values()) {
			when(courses.findCourses(eq(status), any())).thenReturn(pageWithTotal(coursesPerStatus.get(status)));
		}
		when(students.search(any(), any())).thenReturn(pageWithTotal(7L));
		when(assignments.findAll()).thenReturn(List.of(assignment(AssignmentStatus.OPEN)));
		when(submissions.countByStatus(SubmissionStatus.RUNNING)).thenReturn(2L);
		when(submissions.countByStatus(SubmissionStatus.INFRASTRUCTURE_ERROR)).thenReturn(3L);
		MockMvc mockMvc = MockMvcBuilders
			.standaloneSetup(new DashboardController(courses, students, assignments, submissions))
			.build();

		mockMvc.perform(get("/api/v1/dashboard"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.courseCount").value(10))
			.andExpect(jsonPath("$.studentCount").value(7))
			.andExpect(jsonPath("$.openAssignmentCount").value(1))
			.andExpect(jsonPath("$.runningGradingCount").value(2))
			.andExpect(jsonPath("$.failedInfrastructureCount").value(3))
			.andExpect(jsonPath("$.recentActivity").doesNotExist());
	}

	private static <T> Page<T> pageWithTotal(long total) {
		return new PageImpl<>(List.of(), PageRequest.of(0, 1), total);
	}

	private static AssignmentView assignment(AssignmentStatus status) {
		return new AssignmentView(UUID.randomUUID(), UUID.randomUUID(), "a", "Assignment", null, 0, status, true,
				Instant.parse("2026-01-01T00:00:00Z"), Instant.parse("2026-01-02T00:00:00Z"), "UTC", BigDecimal.TEN, 1,
				BigDecimal.TEN, false, null, null, null, null, null, null, null, false, false);
	}

}
