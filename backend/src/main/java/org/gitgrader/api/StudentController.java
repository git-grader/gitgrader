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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.gitgrader.identity.Actor;
import org.gitgrader.identity.ActorProvider;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentRegistry;
import org.gitgrader.identity.StudentSearch;
import org.gitgrader.identity.StudentStatus;
import org.gitgrader.identity.StudentView;
import org.gitgrader.sshkeys.SshKeyOrigin;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.gitgrader.sshkeys.SshKeyView;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Serves instructor student and SSH-key administration. */
@RestController
@RequestMapping("/api/v1/students")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class StudentController {

	private final StudentDirectory students;

	private final StudentRegistry registry;

	private final SshKeyRegistry keys;

	private final ActorProvider actors;

	public StudentController(StudentDirectory students, StudentRegistry registry, SshKeyRegistry keys,
			ActorProvider actors) {
		this.students = students;
		this.registry = registry;
		this.keys = keys;
		this.actors = actors;
	}

	@GetMapping
	public Page<StudentSummary> list(@RequestParam(required = false) @Nullable String query,
			@RequestParam(required = false) @Nullable String classId,
			@RequestParam(required = false) @Nullable StudentStatus status, Pageable pageable) {
		return this.students.search(new StudentSearch(query, status, classId), pageable).map(StudentSummary::from);
	}

	@GetMapping("/{id}")
	public StudentDetail detail(@PathVariable UUID id) {
		StudentView student = requireStudent(id);
		return new StudentDetail(StudentSummary.from(student), this.keys.findAllForStudent(id), List.of());
	}

	@PatchMapping("/{id}/status")
	public StudentView changeStatus(@PathVariable UUID id, @Valid @RequestBody StatusRequest request) {
		Actor actor = this.actors.currentActor();
		return switch (request.status()) {
			case VERIFIED_BY_INSTRUCTOR -> this.registry.verify(id, actor);
			case SUSPENDED -> this.registry.suspend(id, request.reason(), actor);
			case ARCHIVED -> this.registry.archive(id);
			default -> throw new IllegalStateException("Unsupported student status transition");
		};
	}

	@GetMapping("/{id}/keys")
	public List<SshKeyView> keys(@PathVariable UUID id) {
		return this.keys.findAllForStudent(id);
	}

	@PostMapping("/{id}/keys")
	public SshKeyView registerKey(@PathVariable UUID id, @Valid @RequestBody KeyRequest request) {
		return this.keys.register(id, request.label(), request.publicKey(), SshKeyOrigin.INSTRUCTOR,
				this.actors.currentActor().id());
	}

	@PostMapping("/{id}/keys/{keyId}/revoke")
	public SshKeyView revokeKey(@PathVariable UUID id, @PathVariable UUID keyId,
			@Valid @RequestBody ReasonRequest request) {
		requireStudent(id);
		return this.keys.revoke(keyId, request.reason(), this.actors.currentActor().id());
	}

	@PostMapping("/{id}/keys/{keyId}/replace")
	public SshKeyView replaceKey(@PathVariable UUID id, @PathVariable UUID keyId,
			@Valid @RequestBody KeyRequest request) {
		requireStudent(id);
		return this.keys.replace(keyId, request.label(), request.publicKey(), request.reason(),
				this.actors.currentActor().id());
	}

	private StudentView requireStudent(UUID id) {
		return this.students.findById(id).orElseThrow(() -> new IllegalArgumentException("Student not found"));
	}

	/** Student fields shown in collection responses. */
	public record StudentSummary(UUID id, String studentNumber, String firstName, String lastName, String email,
			StudentStatus status) {

		private static StudentSummary from(StudentView student) {
			String[] names = student.fullName().split(" ", 2);
			return new StudentSummary(student.id(), student.studentNumber(), names[0], names.length > 1 ? names[1] : "",
					student.email(), student.status());
		}

	}

	/** Detailed student response. */
	public record StudentDetail(StudentSummary student, List<SshKeyView> sshKeys, List<Object> progress) {
	}

	/** Student lifecycle transition request. */
	public record StatusRequest(@NotNull StudentStatus status, @NotBlank String reason) {
	}

	/** SSH key registration or replacement request. */
	public record KeyRequest(@NotBlank String label, @NotBlank String publicKey, @NotBlank String reason) {
	}

	/** Reason for a key operation. */
	public record ReasonRequest(@NotBlank String reason) {
	}

}
