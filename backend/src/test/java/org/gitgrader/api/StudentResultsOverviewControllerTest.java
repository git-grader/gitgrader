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
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;

import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.identity.StudentView;
import org.gitgrader.security.StudentResultsOverviewTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the results-overview reset endpoint revokes the student's link and refuses to
 * touch anything when the student does not exist.
 */
class StudentResultsOverviewControllerTest {

	private static final UUID STUDENT_ID = UUID.randomUUID();

	private static final StudentView STUDENT_VIEW = new StudentView(STUDENT_ID, "s1000042", "Max Muster",
			"max@example.org", StudentStatus.VERIFIED_BY_INSTRUCTOR, "CLASS-A", Instant.parse("2026-02-01T10:00:00Z"));

	private MockMvc mvc;

	private StudentDirectory students;

	private StudentResultsOverviewTokenService overviewTokens;

	@BeforeEach
	void setUp() {
		this.students = mock(StudentDirectory.class);
		this.overviewTokens = mock(StudentResultsOverviewTokenService.class);
		this.mvc = MockMvcBuilders
			.standaloneSetup(new StudentResultsOverviewController(this.students, this.overviewTokens))
			.build();
	}

	@Test
	@DisplayName("revokes the student's active results-overview link")
	void revokesOverviewLink() throws Exception {
		when(this.students.findById(STUDENT_ID)).thenReturn(Optional.of(STUDENT_VIEW));

		this.mvc.perform(post("/api/v1/students/{id}/results-overview/revoke", STUDENT_ID))
			.andExpect(status().isNoContent());
		verify(this.overviewTokens).revoke(STUDENT_ID);
	}

	@Test
	@DisplayName("refuses to reset the link of an unknown student")
	void refusesUnknownStudent() {
		when(this.students.findById(STUDENT_ID)).thenReturn(Optional.empty());

		assertThatThrownBy(() -> this.mvc.perform(post("/api/v1/students/{id}/results-overview/revoke", STUDENT_ID)))
			.satisfiesAnyOf((error) -> assertThat(error).isInstanceOf(EntityNotFoundException.class),
					(error) -> assertThat(error).hasRootCauseInstanceOf(EntityNotFoundException.class));
		verify(this.overviewTokens, never()).revoke(STUDENT_ID);
	}

}
