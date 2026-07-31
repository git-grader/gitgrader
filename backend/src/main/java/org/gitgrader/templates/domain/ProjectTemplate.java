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

package org.gitgrader.templates.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.gitgrader.templates.ProjectTemplateView;
import org.jspecify.annotations.Nullable;

/** A named student-visible project template. */
@Entity
@Table(name = "project_templates")
public class ProjectTemplate {

	@Id
	private UUID id;

	@Column(name = "template_key", nullable = false)
	private String templateKey;

	@Column(nullable = false)
	private String name;

	@Column
	private @Nullable String description;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected ProjectTemplate() {
	}

	/**
	 * Creates a project template.
	 * @param templateKey stable key
	 * @param name display name
	 * @param description optional description
	 * @param clock application clock
	 */
	public ProjectTemplate(String templateKey, String name, @Nullable String description, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.templateKey = templateKey;
		this.name = name;
		this.description = description;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Returns the template identifier.
	 * @return template identifier
	 */
	public UUID id() {
		return this.id;
	}

	/**
	 * Converts this entity to its public summary.
	 * @return project template view
	 */
	public ProjectTemplateView toView() {
		return new ProjectTemplateView(this.id, this.templateKey, this.name, this.description);
	}

}
