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

package org.gitgrader.templates;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Provides read-only access to student-visible template versions. */
public interface TemplateCatalog {

	/**
	 * Finds a project template summary.
	 * @param id template identifier
	 * @return matching template, if present
	 */
	Optional<ProjectTemplateView> findTemplate(UUID id);

	/**
	 * Lists project templates.
	 * @param pageable requested page and ordering
	 * @return matching templates
	 */
	Page<ProjectTemplateView> findAll(Pageable pageable);

	/**
	 * Finds a template version.
	 * @param id version identifier
	 * @return matching version, if present
	 */
	Optional<TemplateVersionView> findVersion(UUID id);

	/**
	 * Lists versions belonging to a template.
	 * @param templateId template identifier
	 * @return versions in creation order
	 */
	List<TemplateVersionView> findVersions(UUID templateId);

	/**
	 * Lists every published version across all templates, each with its template's name.
	 * @return published versions ordered by template name, then by publication order
	 */
	List<PublishedTemplateVersionView> findPublishedVersions();

}
