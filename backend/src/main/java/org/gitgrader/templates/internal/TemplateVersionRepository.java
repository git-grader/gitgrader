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

package org.gitgrader.templates.internal;

import java.util.List;
import java.util.UUID;

import org.gitgrader.templates.PublishedTemplateVersionView;
import org.gitgrader.templates.domain.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persists student-visible template versions. */
interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {

	/**
	 * Lists versions for a template.
	 * @param templateId template identifier
	 * @return versions in creation order
	 */
	List<TemplateVersion> findByTemplateIdOrderByCreatedAtAsc(UUID templateId);

	/**
	 * Lists every published version across all templates, with its template's name.
	 *
	 * <p>
	 * One query rather than one per template: the assignment picker needs the whole set
	 * at once, and walking it template by template made opening the page cost hundreds of
	 * requests. There is no mapped association between the two entities, so they are
	 * joined on the identifier the version stores.
	 * @return published versions ordered by template name, then by publication order
	 */
	@Query("""
			select new org.gitgrader.templates.PublishedTemplateVersionView(v.id, t.name, v.versionLabel)
			from TemplateVersion v, ProjectTemplate t
			where v.templateId = t.id and v.publishedAt is not null
			order by t.name asc, v.createdAt asc
			""")
	List<PublishedTemplateVersionView> findPublished();

}
