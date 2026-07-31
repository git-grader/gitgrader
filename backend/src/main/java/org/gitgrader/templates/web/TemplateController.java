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

package org.gitgrader.templates.web;

import java.nio.file.Path;
import java.security.Principal;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.templates.ProjectTemplateView;
import org.gitgrader.templates.TemplateAdministration;
import org.gitgrader.templates.TemplateCatalog;
import org.gitgrader.templates.TemplateContentGuard;
import org.gitgrader.templates.TemplateVersionView;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** Serves project-template administration without exposing storage paths to clients. */
@RestController
@RequestMapping("/api/v1/templates")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class TemplateController {

	private final TemplateCatalog catalog;

	private final TemplateAdministration administration;

	private final StorageProperties storage;

	private final SecureZipExtractor extractor;

	private final TemplateContentGuard contentGuard;

	public TemplateController(TemplateCatalog catalog, TemplateAdministration administration, StorageProperties storage,
			SecureZipExtractor extractor, TemplateContentGuard contentGuard) {
		this.catalog = catalog;
		this.administration = administration;
		this.storage = storage;
		this.extractor = extractor;
		this.contentGuard = contentGuard;
	}

	/**
	 * Lists project templates.
	 * @param pageable requested page
	 * @return templates ordered by key by default
	 */
	@GetMapping
	public Page<ProjectTemplateView> list(Pageable pageable) {
		Pageable ordered = pageable.getSort().isSorted() ? pageable
				: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("templateKey").ascending());
		return this.catalog.findAll(ordered);
	}

	/**
	 * Creates a project template.
	 * @param request template fields
	 * @return created template summary
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ProjectTemplateView create(@Valid @RequestBody CreateTemplateRequest request) {
		UUID id = this.administration.createTemplate(request.templateKey(), request.name(), request.description());
		return new ProjectTemplateView(id, request.templateKey(), request.name(), request.description());
	}

	/**
	 * Lists versions for a template.
	 * @param id template identifier
	 * @return versions in creation order
	 */
	@GetMapping("/{id}/versions")
	public List<TemplateVersionView> versions(@PathVariable UUID id) {
		return this.catalog.findVersions(id);
	}

	/**
	 * Securely uploads a new template version.
	 * @param id template identifier
	 * @param file ZIP upload
	 * @param versionLabel version label
	 * @return created version draft
	 */
	@PostMapping("/{id}/versions")
	@ResponseStatus(HttpStatus.CREATED)
	public TemplateVersionView upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
			@RequestParam @NotBlank String versionLabel) {
		ProjectTemplateView template = findTemplate(id);
		String relative = safeSegment(template.templateKey()) + "/" + safeSegment(versionLabel);
		Path destination = StorageProperties.resolveInside(this.storage.templates(), relative);
		this.extractor.extract(file, destination);
		try {
			// Checked here as well as at publication so the instructor is told at once,
			// and so material that may never be handed to a student is not kept at all.
			this.contentGuard.validate(destination);
			return this.administration.createVersion(id, versionLabel, relative);
		}
		catch (RuntimeException exception) {
			SecureZipExtractor.delete(destination);
			throw exception;
		}
	}

	/**
	 * Publishes a template version after content validation.
	 * @param versionId version identifier
	 * @param principal authenticated instructor
	 * @return immutable published version
	 */
	@PostMapping("/versions/{versionId}/publish")
	public TemplateVersionView publish(@PathVariable UUID versionId, Principal principal) {
		return this.administration.publish(versionId, principal.getName());
	}

	private ProjectTemplateView findTemplate(UUID id) {
		return this.catalog.findTemplate(id).orElseThrow(() -> new IllegalArgumentException("Template not found"));
	}

	private static String safeSegment(String value) {
		String safe = value.replaceAll("[^a-zA-Z0-9._-]", "_");
		if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
			throw new ArchiveUploadException("The template key and version label must contain a safe path segment.");
		}
		return safe;
	}

	/**
	 * Template creation fields.
	 *
	 * @param templateKey stable template key
	 * @param name display name
	 * @param description optional description
	 */
	public record CreateTemplateRequest(@NotBlank String templateKey, @NotBlank String name,
			@Nullable String description) {
	}

}
