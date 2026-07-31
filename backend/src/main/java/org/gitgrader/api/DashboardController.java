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

import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentSearch;
import org.gitgrader.submissions.SubmissionService;
import org.gitgrader.submissions.SubmissionStatus;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Serves the instructor dashboard summary. */
@RestController
@RequestMapping("/api/v1/dashboard")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class DashboardController {

	private static final PageRequest COUNT_PAGE = PageRequest.of(0, 1);

	private final CourseCatalog courses;

	private final StudentDirectory students;

	private final AssignmentCatalog assignments;

	private final SubmissionService submissions;

	public DashboardController(CourseCatalog courses, StudentDirectory students, AssignmentCatalog assignments,
			SubmissionService submissions) {
		this.courses = courses;
		this.students = students;
		this.assignments = assignments;
		this.submissions = submissions;
	}

	/**
	 * Returns current platform totals needed by the dashboard cards.
	 * @return dashboard summary
	 */
	@GetMapping
	public DashboardView dashboard() {
		long courseCount = 0;
		for (CourseStatus status : CourseStatus.values()) {
			courseCount += this.courses.findCourses(status, COUNT_PAGE).getTotalElements();
		}
		long studentCount = this.students.search(new StudentSearch(null, null, null), COUNT_PAGE).getTotalElements();
		long openAssignments = this.assignments.findAll()
			.stream()
			.filter((assignment) -> assignment.status() == AssignmentStatus.OPEN)
			.count();
		return new DashboardView(courseCount, studentCount, openAssignments,
				this.submissions.countByStatus(SubmissionStatus.RUNNING),
				this.submissions.countByStatus(SubmissionStatus.INFRASTRUCTURE_ERROR), List.of());
	}

	/**
	 * Dashboard counters and recent activity.
	 *
	 * @param courseCount number of courses
	 * @param studentCount number of students
	 * @param openAssignmentCount number of assignments accepting submissions
	 * @param runningGradingCount number of grading submissions currently running
	 * @param failedInfrastructureCount number of submissions blocked by infrastructure
	 * @param recentActivity recent activity items, empty when no inexpensive source
	 * exists
	 */
	public record DashboardView(long courseCount, long studentCount, long openAssignmentCount, long runningGradingCount,
			long failedInfrastructureCount, List<Object> recentActivity) {
	}

}
