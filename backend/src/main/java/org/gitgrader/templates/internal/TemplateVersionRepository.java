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

import org.gitgrader.templates.domain.TemplateVersion;
import org.springframework.data.jpa.repository.JpaRepository;

/** Persists student-visible template versions. */
interface TemplateVersionRepository extends JpaRepository<TemplateVersion, UUID> {

	/**
	 * Lists versions for a template.
	 * @param templateId template identifier
	 * @return versions in creation order
	 */
	List<TemplateVersion> findByTemplateIdOrderByCreatedAtAsc(UUID templateId);

}
