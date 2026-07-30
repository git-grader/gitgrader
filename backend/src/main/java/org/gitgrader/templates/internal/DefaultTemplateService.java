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

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.templates.TemplateAdministration;
import org.gitgrader.templates.TemplateCatalog;
import org.gitgrader.templates.TemplateContentGuard;
import org.gitgrader.templates.TemplateVersionView;
import org.gitgrader.templates.domain.ProjectTemplate;
import org.gitgrader.templates.domain.TemplateVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Default student-visible template catalog and administration implementation. */
@Service
@Transactional
public class DefaultTemplateService implements TemplateCatalog, TemplateAdministration {

	private final ProjectTemplateRepository templates;

	private final TemplateVersionRepository versions;

	private final TemplateContentGuard contentGuard;

	private final StorageProperties storage;

	private final Clock clock;

	DefaultTemplateService(ProjectTemplateRepository templates, TemplateVersionRepository versions,
			TemplateContentGuard contentGuard, StorageProperties storage, Clock clock) {
		this.templates = templates;
		this.versions = versions;
		this.contentGuard = contentGuard;
		this.storage = storage;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TemplateVersionView> findVersion(UUID id) {
		return this.versions.findById(id).map(TemplateVersion::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TemplateVersionView> findVersions(UUID templateId) {
		return this.versions.findByTemplateIdOrderByCreatedAtAsc(templateId)
			.stream()
			.map(TemplateVersion::toView)
			.toList();
	}

	@Override
	public UUID createTemplate(String templateKey, String name, @Nullable String description) {
		return this.templates.save(new ProjectTemplate(templateKey, name, description, this.clock)).id();
	}

	@Override
	public TemplateVersionView createVersion(UUID templateId, String versionLabel, String storagePath) {
		requireTemplate(templateId);
		StorageProperties.resolveInside(this.storage.templates(), storagePath);
		return this.versions.save(new TemplateVersion(templateId, versionLabel, storagePath, this.clock)).toView();
	}

	@Override
	public TemplateVersionView publish(UUID versionId, String actor) {
		TemplateVersion version = requireVersion(versionId);
		Path directory = StorageProperties.resolveInside(this.storage.templates(), version.storagePath());
		this.contentGuard.validate(directory);
		ContentSnapshot snapshot = ContentHasher.snapshot(directory);
		version.publish(snapshot.hash(), snapshot.fileCount(), snapshot.totalBytes(), actor, this.clock);
		return this.versions.save(version).toView();
	}

	private void requireTemplate(UUID id) {
		if (!this.templates.existsById(id)) {
			throw new EntityNotFoundException("Project template not found: " + id);
		}
	}

	private TemplateVersion requireVersion(UUID id) {
		return this.versions.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Template version not found: " + id));
	}

}
