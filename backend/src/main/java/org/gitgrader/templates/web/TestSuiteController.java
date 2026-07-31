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
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.templates.TestSuiteAdministration;
import org.gitgrader.templates.TestSuiteCatalog;
import org.gitgrader.templates.TestSuiteVersionView;
import org.gitgrader.templates.TestSuiteView;
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

/** Serves hidden test-suite metadata while never exposing uploaded content. */
@RestController
@RequestMapping("/api/v1/test-suites")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class TestSuiteController {

	private final TestSuiteCatalog catalog;

	private final TestSuiteAdministration administration;

	private final StorageProperties storage;

	private final SecureZipExtractor extractor;

	public TestSuiteController(TestSuiteCatalog catalog, TestSuiteAdministration administration,
			StorageProperties storage, SecureZipExtractor extractor) {
		this.catalog = catalog;
		this.administration = administration;
		this.storage = storage;
		this.extractor = extractor;
	}

	/**
	 * Lists test-suite summaries without filenames or content.
	 * @param pageable requested page
	 * @return suites ordered by key by default
	 */
	@GetMapping
	public Page<TestSuiteView> list(Pageable pageable) {
		Pageable ordered = pageable.getSort().isSorted() ? pageable
				: PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), Sort.by("suiteKey").ascending());
		return this.catalog.findAll(ordered);
	}

	/**
	 * Creates a hidden test suite.
	 * @param request suite fields
	 * @return created suite summary
	 */
	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public TestSuiteView create(@Valid @RequestBody CreateTestSuiteRequest request) {
		UUID id = this.administration.createTestSuite(request.suiteKey(), request.name(), request.description());
		return new TestSuiteView(id, request.suiteKey(), request.name(), request.description());
	}

	/**
	 * Lists version metadata for a test suite without content details.
	 * @param id suite identifier
	 * @return versions in creation order
	 */
	@GetMapping("/{id}/versions")
	public List<TestSuiteVersionView> versions(@PathVariable UUID id) {
		return this.catalog.findVersions(id);
	}

	/**
	 * Securely uploads a hidden test-suite version under the hidden-test root.
	 * @param id suite identifier
	 * @param file ZIP upload
	 * @param versionLabel version label
	 * @return created version draft
	 */
	@PostMapping("/{id}/versions")
	@ResponseStatus(HttpStatus.CREATED)
	public TestSuiteVersionView upload(@PathVariable UUID id, @RequestParam("file") MultipartFile file,
			@RequestParam @NotBlank String versionLabel) {
		TestSuiteView suite = this.catalog.findTestSuite(id)
			.orElseThrow(() -> new IllegalArgumentException("Test suite not found"));
		String relative = safeSegment(suite.suiteKey()) + "/" + safeSegment(versionLabel);
		Path destination = StorageProperties.resolveInside(this.storage.tests(), relative);
		this.extractor.extract(file, destination);
		try {
			return this.administration.createVersion(id, versionLabel, relative);
		}
		catch (RuntimeException exception) {
			SecureZipExtractor.delete(destination);
			throw exception;
		}
	}

	/**
	 * Publishes a hidden test-suite version.
	 * @param versionId version identifier
	 * @param request declared test counts
	 * @param principal authenticated instructor
	 * @return immutable published version metadata
	 */
	@PostMapping("/versions/{versionId}/publish")
	public TestSuiteVersionView publish(@PathVariable UUID versionId,
			@Valid @RequestBody PublishTestSuiteRequest request, Principal principal) {
		return this.administration.publish(versionId, principal.getName(), request.hiddenTestCount(),
				request.publicTestCount());
	}

	private static String safeSegment(String value) {
		String safe = value.replaceAll("[^a-zA-Z0-9._-]", "_");
		if (safe.isBlank() || ".".equals(safe) || "..".equals(safe)) {
			throw new ArchiveUploadException("The suite key and version label must contain a safe path segment.");
		}
		return safe;
	}

	/**
	 * Test-suite creation fields.
	 *
	 * @param suiteKey stable suite key
	 * @param name display name
	 * @param description optional description
	 */
	public record CreateTestSuiteRequest(@NotBlank String suiteKey, @NotBlank String name,
			@Nullable String description) {
	}

	/**
	 * Declared hidden and public test counts for publication metadata.
	 *
	 * @param hiddenTestCount number of hidden tests
	 * @param publicTestCount number of public tests
	 */
	public record PublishTestSuiteRequest(@NotNull @Min(0) Integer hiddenTestCount,
			@NotNull @Min(0) Integer publicTestCount) {
	}

}
