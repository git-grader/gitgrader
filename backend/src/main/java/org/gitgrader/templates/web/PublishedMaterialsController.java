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

import java.util.List;

import org.gitgrader.templates.PublishedTemplateVersionView;
import org.gitgrader.templates.PublishedTestSuiteVersionView;
import org.gitgrader.templates.TemplateCatalog;
import org.gitgrader.templates.TestSuiteCatalog;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves everything an assignment can be pointed at, in one response.
 *
 * <p>
 * The assignment form previously assembled this itself: it listed the templates, listed
 * the suites, and then asked each one for its versions, so a course with a hundred of
 * each cost hundreds of requests on every cold load and the form waited on all of them.
 * The set is small once published versions are the only thing selected, so it is cheaper
 * to compute it here in two queries than to have the browser rebuild it.
 */
@RestController
@RequestMapping("/api/v1/materials")
@PreAuthorize("hasAnyRole('INSTRUCTOR','ADMIN')")
public class PublishedMaterialsController {

	private final TemplateCatalog templates;

	private final TestSuiteCatalog suites;

	public PublishedMaterialsController(TemplateCatalog templates, TestSuiteCatalog suites) {
		this.templates = templates;
		this.suites = suites;
	}

	/**
	 * Lists every published template version and test-suite version.
	 * @return the choices an assignment can be pointed at
	 */
	@GetMapping("/published")
	public PublishedMaterials published() {
		return new PublishedMaterials(this.templates.findPublishedVersions(), this.suites.findPublishedVersions());
	}

	/**
	 * The published material an assignment can reference.
	 *
	 * @param templateVersions published template versions, by template name
	 * @param suiteVersions published test-suite versions, by suite name
	 */
	public record PublishedMaterials(List<PublishedTemplateVersionView> templateVersions,
			List<PublishedTestSuiteVersionView> suiteVersions) {
	}

}
