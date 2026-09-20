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
import java.util.UUID;

import org.gitgrader.identity.Actor;
import org.gitgrader.identity.ActorProvider;
import org.gitgrader.identity.ActorType;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentRegistry;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.identity.StudentView;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the status transition endpoint routes each documented request value to the right
 * registry transition. A bracket in {@code StatusRequest} used to accept the whole
 * student lifecycle and answer 409 for the one value it did not implement; this guards
 * the endpoint's contract (verify, suspend, archive, restore) against that returning.
 */
class StudentControllerTest {

	private static final UUID STUDENT_ID = UUID.randomUUID();

	private static final Actor INSTRUCTOR = new Actor(ActorType.INSTRUCTOR, "t.teacher", "Test Teacher");

	private static final StudentView STUDENT_VIEW = new StudentView(STUDENT_ID, "s1000042", "Max Muster",
			"max@example.org", StudentStatus.VERIFIED_BY_INSTRUCTOR, "CLASS-A", Instant.parse("2026-02-01T10:00:00Z"));

	private MockMvc mvc;

	private StudentDirectory students;

	private StudentRegistry registry;

	private SshKeyRegistry keys;

	@BeforeEach
	void setUp() {
		this.students = mock(StudentDirectory.class);
		this.registry = mock(StudentRegistry.class);
		this.keys = mock(SshKeyRegistry.class);
		ActorProvider actors = () -> INSTRUCTOR;
		this.mvc = MockMvcBuilders
			.standaloneSetup(new StudentController(this.students, this.registry, this.keys, actors))
			.build();
	}

	@Test
	@DisplayName("routes an instructor verification to the verify transition")
	void routesVerificationToVerify() throws Exception {
		when(this.registry.verify(STUDENT_ID, INSTRUCTOR)).thenReturn(STUDENT_VIEW);

		this.mvc.perform(patch("/api/v1/students/{id}/status", STUDENT_ID).contentType("application/json").content("""
				{"status":"VERIFIED_BY_INSTRUCTOR","reason":"confirmed identity"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("VERIFIED_BY_INSTRUCTOR"));
		verify(this.registry).verify(STUDENT_ID, INSTRUCTOR);
	}

	@Test
	@DisplayName("routes a suspension request to the suspend transition")
	void routesSuspensionToSuspend() throws Exception {
		when(this.registry.suspend(STUDENT_ID, "suspected plagiarism", INSTRUCTOR)).thenReturn(STUDENT_VIEW);

		this.mvc.perform(patch("/api/v1/students/{id}/status", STUDENT_ID).contentType("application/json").content("""
				{"status":"SUSPENDED","reason":"suspected plagiarism"}""")).andExpect(status().isOk());
		verify(this.registry).suspend(STUDENT_ID, "suspected plagiarism", INSTRUCTOR);
	}

	@Test
	@DisplayName("routes an archiving request to the archive transition")
	void routesArchiveToArchive() throws Exception {
		when(this.registry.archive(STUDENT_ID)).thenReturn(new StudentView(STUDENT_ID, "s1000042", "Max Muster",
				"max@example.org", StudentStatus.ARCHIVED, "CLASS-A", Instant.parse("2026-02-01T10:00:00Z")));

		this.mvc.perform(patch("/api/v1/students/{id}/status", STUDENT_ID).contentType("application/json").content("""
				{"status":"ARCHIVED","reason":"Archived by instructor"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("ARCHIVED"));
		verify(this.registry).archive(STUDENT_ID);
	}

	@Test
	@DisplayName("routes a restore request to the restore transition")
	void routesRestoreToRestore() throws Exception {
		when(this.registry.restore(STUDENT_ID, INSTRUCTOR)).thenReturn(STUDENT_VIEW);

		this.mvc.perform(patch("/api/v1/students/{id}/status", STUDENT_ID).contentType("application/json").content("""
				{"status":"RESTORE","reason":"Restored by instructor"}"""))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("VERIFIED_BY_INSTRUCTOR"));
		verify(this.registry).restore(STUDENT_ID, INSTRUCTOR);
	}

	@Test
	@DisplayName("refuses a status the request contract does not state")
	void refusesUndocumentedTransition() throws Exception {
		this.mvc.perform(patch("/api/v1/students/{id}/status", STUDENT_ID).contentType("application/json").content("""
				{"status":"SELF_REGISTERED","reason":"not an instructor target"}"""))
			.andExpect(status().isBadRequest());
	}

}
