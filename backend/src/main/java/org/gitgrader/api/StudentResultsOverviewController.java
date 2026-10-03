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

import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.security.StudentResultsOverviewTokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Instructor administration of a student's results-overview link.
 *
 * <p>
 * Kept out of {@link StudentController} so that controller stays within the project's
 * class fan-out budget. Resetting is deliberately a separate capability from editing the
 * student: it invalidates a bearer secret that may already have been handed out.
 */
@RestController
@RequestMapping("/api/v1/students/{id}/results-overview")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class StudentResultsOverviewController {

	private final StudentDirectory students;

	private final StudentResultsOverviewTokenService overviewTokens;

	public StudentResultsOverviewController(StudentDirectory students,
			StudentResultsOverviewTokenService overviewTokens) {
		this.students = students;
		this.overviewTokens = overviewTokens;
	}

	/**
	 * Withdraws the student's current results-overview link so a link that has already
	 * been shared stops working. The next accepted push issues a fresh one, so the
	 * student is not left without a link permanently.
	 * @param id the student whose link should be reset
	 */
	@PostMapping("/revoke")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	public void revoke(@PathVariable UUID id) {
		if (this.students.findById(id).isEmpty()) {
			throw new EntityNotFoundException("Student not found");
		}
		this.overviewTokens.revoke(id);
	}

}
