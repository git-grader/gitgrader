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

package org.gitgrader.assignments.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.gitgrader.assignments.AssignmentAdministration;
import org.gitgrader.assignments.AssignmentCatalog;
import org.gitgrader.assignments.AssignmentDefinition;
import org.gitgrader.assignments.AssignmentStatus;
import org.gitgrader.assignments.AssignmentView;
import org.gitgrader.assignments.DeadlineExtensionView;
import org.gitgrader.identity.ActorProvider;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Serves assignment lifecycle and extension administration. */
@RestController
@RequestMapping("/api/v1/assignments")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class AssignmentController {

	private final AssignmentCatalog catalog;

	private final AssignmentAdministration administration;

	private final ActorProvider actors;

	public AssignmentController(AssignmentCatalog catalog, AssignmentAdministration administration,
			ActorProvider actors) {
		this.catalog = catalog;
		this.administration = administration;
		this.actors = actors;
	}

	@GetMapping
	public Page<AssignmentView> list(@RequestParam(required = false) @Nullable UUID courseId,
			@RequestParam(required = false) @Nullable AssignmentStatus status, Pageable pageable) {
		List<AssignmentView> assignments = (courseId == null ? this.catalog.findAll()
				: this.catalog.findByCourse(courseId))
			.stream()
			.filter((item) -> status == null || item.status() == status)
			.toList();
		int start = Math.min((int) pageable.getOffset(), assignments.size());
		int end = Math.min(start + pageable.getPageSize(), assignments.size());
		return new PageImpl<>(assignments.subList(start, end), pageable, assignments.size());
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public AssignmentView create(@Valid @RequestBody AssignmentDefinition definition) {
		return this.administration.create(definition);
	}

	/**
	 * Updates a draft assignment.
	 * @param id assignment identifier
	 * @param definition replacement assignment values
	 * @return updated assignment
	 */
	@PutMapping("/{id}")
	public AssignmentView update(@PathVariable UUID id, @Valid @RequestBody AssignmentDefinition definition) {
		return this.administration.update(id, definition);
	}

	@GetMapping("/{id}")
	public AssignmentView detail(@PathVariable UUID id) {
		return this.catalog.findAssignment(id).orElseThrow(() -> new IllegalArgumentException("Assignment not found"));
	}

	@PostMapping("/{id}/publish")
	public AssignmentView publish(@PathVariable UUID id) {
		// OPEN rather than SCHEDULED: admission gates on opensAt independently of the
		// status, so publishing early cannot let a student submit before the assignment
		// opens. The database also refuses to leave DRAFT without a template, a test
		// suite, a runtime and both dates.
		return this.administration.changeStatus(id, AssignmentStatus.OPEN);
	}

	@GetMapping("/{id}/extensions")
	public List<DeadlineExtensionView> extensions(@PathVariable UUID id) {
		detail(id);
		return List.of();
	}

	@PostMapping("/{id}/extensions")
	@ResponseStatus(HttpStatus.CREATED)
	public DeadlineExtensionView grantExtension(@PathVariable UUID id, @Valid @RequestBody ExtensionRequest request) {
		return this.administration.grantExtension(id, request.studentId(), request.extendedDueAt(), request.reason(),
				this.actors.currentActor().id());
	}

	@DeleteMapping("/{id}/extensions/{extensionId}")
	public DeadlineExtensionView revokeExtension(@PathVariable UUID id, @PathVariable UUID extensionId) {
		detail(id);
		return this.administration.revokeExtension(extensionId, this.actors.currentActor().id());
	}

	/** Deadline extension request. */
	public record ExtensionRequest(@NotNull UUID studentId, @NotNull @Future Instant extendedDueAt,
			@NotBlank String reason) {
	}

}
