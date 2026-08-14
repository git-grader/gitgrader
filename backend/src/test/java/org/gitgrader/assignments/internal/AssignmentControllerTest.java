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

package org.gitgrader.assignments.internal;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

import org.gitgrader.api.GlobalExceptionHandler;
import org.gitgrader.assignments.AssignmentDefinition;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.domain.Assignment;
import org.gitgrader.assignments.domain.DeadlineExtension;
import org.gitgrader.assignments.web.AssignmentController;
import org.gitgrader.assignments.web.AssignmentExceptionHandler;
import org.gitgrader.identity.ActorProvider;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssignmentControllerTest {

	private static final Instant OPENS = Instant.parse("2026-03-01T10:00:00Z");

	private static final Instant DUE = Instant.parse("2026-03-01T12:00:00Z");

	private static final Clock CLOCK = Clock.fixed(OPENS.minusSeconds(60), ZoneOffset.UTC);

	@Test
	void updatingDraftChangesExerciseMaterial() throws Exception {
		UUID courseId = UUID.randomUUID();
		UUID templateVersionId = UUID.randomUUID();
		UUID testSuiteVersionId = UUID.randomUUID();
		UUID runtimeId = UUID.randomUUID();
		Assignment assignment = assignment(courseId, "assignment-1", AssignmentStatus.DRAFT);
		MockMvc mockMvc = mockMvc(assignment);

		mockMvc
			.perform(put("/api/v1/assignments/{id}", assignment.toView().id()).contentType("application/json")
				.content(definition(courseId, "assignment-1", templateVersionId, testSuiteVersionId, runtimeId)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DRAFT"))
			.andExpect(jsonPath("$.templateVersionId").value(templateVersionId.toString()))
			.andExpect(jsonPath("$.testSuiteVersionId").value(testSuiteVersionId.toString()))
			.andExpect(jsonPath("$.runtimeId").value(runtimeId.toString()));
	}

	@Test
	void draftMayBeSavedBeforeItIsComplete() throws Exception {
		UUID courseId = UUID.randomUUID();
		UUID templateVersionId = UUID.randomUUID();
		Assignment assignment = assignment(courseId, "assignment-1", AssignmentStatus.DRAFT);

		mockMvc(assignment)
			.perform(put("/api/v1/assignments/{id}", assignment.toView().id()).contentType("application/json")
				.content(definition(courseId, "assignment-1", templateVersionId, null, null)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.templateVersionId").value(templateVersionId.toString()))
			.andExpect(jsonPath("$.testSuiteVersionId").doesNotExist())
			.andExpect(jsonPath("$.runtimeId").doesNotExist());
	}

	@Test
	void updatingPublishedAssignmentReturns409() throws Exception {
		UUID courseId = UUID.randomUUID();
		Assignment assignment = assignment(courseId, "assignment-1", AssignmentStatus.OPEN);

		mockMvc(assignment)
			.perform(put("/api/v1/assignments/{id}", assignment.toView().id()).contentType("application/json")
				.content(completeDefinition(courseId, "assignment-1")))
			.andExpect(status().isConflict());
	}

	@Test
	void changingCourseIdReturns400() throws Exception {
		Assignment assignment = assignment(UUID.randomUUID(), "assignment-1", AssignmentStatus.DRAFT);

		mockMvc(assignment)
			.perform(put("/api/v1/assignments/{id}", assignment.toView().id()).contentType("application/json")
				.content(completeDefinition(UUID.randomUUID(), "assignment-1")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("courseId cannot be changed"));
	}

	@Test
	void changingAssignmentKeyReturns400() throws Exception {
		UUID courseId = UUID.randomUUID();
		Assignment assignment = assignment(courseId, "assignment-1", AssignmentStatus.DRAFT);

		mockMvc(assignment)
			.perform(put("/api/v1/assignments/{id}", assignment.toView().id()).contentType("application/json")
				.content(completeDefinition(courseId, "assignment-2")))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.detail").value("assignmentKey cannot be changed"));
	}

	@Test
	void updatingUnknownAssignmentReturns404() throws Exception {
		UUID courseId = UUID.randomUUID();

		mockMvc()
			.perform(put("/api/v1/assignments/{id}", UUID.randomUUID()).contentType("application/json")
				.content(completeDefinition(courseId, "assignment-1")))
			.andExpect(status().isNotFound());
	}

	@Test
	void assignmentCanBePublishedAfterUpdateSuppliesMissingMaterial() throws Exception {
		UUID courseId = UUID.randomUUID();
		Assignment assignment = assignment(courseId, "assignment-1", AssignmentStatus.DRAFT);
		MockMvc mockMvc = mockMvc(assignment);
		UUID assignmentId = assignment.toView().id();

		mockMvc
			.perform(put("/api/v1/assignments/{id}", assignmentId).contentType("application/json")
				.content(completeDefinition(courseId, "assignment-1")))
			.andExpect(status().isOk());
		mockMvc.perform(post("/api/v1/assignments/{id}/publish", assignmentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("OPEN"));
	}

	@Test
	void grantedExtensionsAreListedRatherThanReportedAsNone() throws Exception {
		UUID courseId = UUID.randomUUID();
		Assignment assignment = assignment(courseId, "assignment-1", AssignmentStatus.OPEN);
		UUID assignmentId = assignment.toView().id();
		AssignmentRepository assignments = mock(AssignmentRepository.class);
		DeadlineExtensionRepository extensions = mock(DeadlineExtensionRepository.class);
		when(assignments.findById(assignmentId)).thenReturn(Optional.of(assignment));
		when(extensions.findByAssignmentIdOrderByGrantedAtDesc(assignmentId))
			.thenReturn(List.of(new DeadlineExtension(assignmentId, UUID.randomUUID(), DUE.plusSeconds(3600),
					"hospital appointment", "instructor", CLOCK)));

		mockMvc(assignments, extensions).perform(get("/api/v1/assignments/{id}/extensions", assignmentId))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.length()").value(1))
			.andExpect(jsonPath("$[0].reason").value("hospital appointment"));
	}

	@Test
	void listingExtensionsOfUnknownAssignmentReturns404() throws Exception {
		mockMvc().perform(get("/api/v1/assignments/{id}/extensions", UUID.randomUUID()))
			.andExpect(status().isNotFound());
	}

	private static MockMvc mockMvc(Assignment assignment) {
		return mockMvc(Optional.of(assignment));
	}

	private static MockMvc mockMvc() {
		return mockMvc(Optional.empty());
	}

	private static MockMvc mockMvc(Optional<Assignment> assignment) {
		AssignmentRepository assignments = mock(AssignmentRepository.class);
		DeadlineExtensionRepository extensions = mock(DeadlineExtensionRepository.class);
		assignment.ifPresent((value) -> when(assignments.findById(value.toView().id())).thenReturn(Optional.of(value)));
		when(assignments.save(any(Assignment.class))).thenAnswer((invocation) -> invocation.getArgument(0));
		return mockMvc(assignments, extensions);
	}

	private static MockMvc mockMvc(AssignmentRepository assignments, DeadlineExtensionRepository extensions) {
		DefaultAssignmentService service = new DefaultAssignmentService(assignments, extensions, CLOCK);
		return MockMvcBuilders.standaloneSetup(new AssignmentController(service, service, mock(ActorProvider.class)))
			.setControllerAdvice(new GlobalExceptionHandler(), new AssignmentExceptionHandler())
			.build();
	}

	private static Assignment assignment(UUID courseId, String assignmentKey, AssignmentStatus status) {
		UUID templateVersionId = status == AssignmentStatus.DRAFT ? null : UUID.randomUUID();
		UUID testSuiteVersionId = status == AssignmentStatus.DRAFT ? null : UUID.randomUUID();
		UUID runtimeId = status == AssignmentStatus.DRAFT ? null : UUID.randomUUID();
		return new Assignment(new AssignmentDefinition(courseId, assignmentKey, "Assignment", null, 0, status, true,
				OPENS, DUE, "UTC", BigDecimal.valueOf(100), 10, BigDecimal.valueOf(100), false, templateVersionId,
				testSuiteVersionId, runtimeId, 60, 1024L, BigDecimal.ONE, 16, false), CLOCK);
	}

	private static String completeDefinition(UUID courseId, String assignmentKey) {
		return definition(courseId, assignmentKey, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
	}

	private static String json(@Nullable UUID id) {
		return id == null ? "null" : "\"" + id + "\"";
	}

	private static String definition(UUID courseId, String assignmentKey, @Nullable UUID templateVersionId,
			@Nullable UUID testSuiteVersionId, @Nullable UUID runtimeId) {
		return """
				{
				  "courseId":"%s",
				  "assignmentKey":"%s",
				  "title":"Updated Assignment",
				  "description":"Updated description",
				  "displayOrder":1,
				  "status":"DRAFT",
				  "mandatory":true,
				  "opensAt":"%s",
				  "dueAt":"%s",
				  "timezone":"UTC",
				  "maxPoints":100,
				  "testCount":10,
				  "passThreshold":80,
				  "allowLate":false,
				  "templateVersionId":%s,
				  "testSuiteVersionId":%s,
				  "runtimeId":%s,
				  "timeoutSeconds":60,
				  "memoryLimitBytes":1024,
				  "cpuLimit":1,
				  "pidLimit":16,
				  "networkEnabled":false
				}
				""".formatted(courseId, assignmentKey, OPENS, DUE, json(templateVersionId), json(testSuiteVersionId),
				json(runtimeId));
	}

}
