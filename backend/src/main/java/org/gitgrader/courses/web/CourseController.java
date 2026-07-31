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

package org.gitgrader.courses.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.gitgrader.courses.CourseAdministration;
import org.gitgrader.courses.CourseCatalog;
import org.gitgrader.courses.CourseClassView;
import org.gitgrader.courses.CourseDefinition;
import org.gitgrader.courses.CourseStatus;
import org.gitgrader.courses.CourseView;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Serves course and class administration. */
@RestController
@RequestMapping("/api/v1/courses")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class CourseController {

	private final CourseCatalog catalog;

	private final CourseAdministration administration;

	public CourseController(CourseCatalog catalog, CourseAdministration administration) {
		this.catalog = catalog;
		this.administration = administration;
	}

	@GetMapping
	public Page<CourseView> list(@RequestParam(defaultValue = "ACTIVE") CourseStatus status, Pageable pageable) {
		return this.catalog.findCourses(status, pageable);
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public CourseView create(@Valid @RequestBody CourseDefinition definition) {
		return this.administration.createCourse(definition);
	}

	/**
	 * Updates a course.
	 * @param id course identifier
	 * @param definition replacement course values
	 * @return updated course
	 */
	@PutMapping("/{id}")
	public CourseView update(@PathVariable UUID id, @Valid @RequestBody CourseDefinition definition) {
		return this.administration.update(id, definition);
	}

	@GetMapping("/{id}")
	public CourseView detail(@PathVariable UUID id) {
		return this.catalog.findCourse(id).orElseThrow(() -> new IllegalArgumentException("Course not found"));
	}

	@GetMapping("/{id}/classes")
	public List<CourseClassView> classes(@PathVariable UUID id) {
		return this.catalog.findClasses(id);
	}

	@PostMapping("/{id}/classes")
	@ResponseStatus(HttpStatus.CREATED)
	public CourseClassView createClass(@PathVariable UUID id, @Valid @RequestBody ClassRequest request) {
		return this.administration.createClass(id, request.classKey(), request.name());
	}

	/**
	 * Updates a class in a course.
	 * @param id owning course identifier
	 * @param classId class identifier
	 * @param request replacement class values
	 * @return updated class
	 */
	@PutMapping("/{id}/classes/{classId}")
	public CourseClassView updateClass(@PathVariable UUID id, @PathVariable UUID classId,
			@Valid @RequestBody ClassRequest request) {
		return this.administration.updateClass(id, classId, request.classKey(), request.name());
	}

	/** Course class creation request. */
	public record ClassRequest(@NotBlank String classKey, @NotBlank String name) {
	}

}
