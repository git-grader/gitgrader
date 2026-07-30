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

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Creates templates and permanently publishes their versions. */
public interface TemplateAdministration {

	/**
	 * Creates a project template.
	 * @param templateKey stable template key
	 * @param name display name
	 * @param description optional description
	 * @return template identifier
	 */
	UUID createTemplate(String templateKey, String name, @Nullable String description);

	/**
	 * Creates a version draft whose path is relative to template storage.
	 * @param templateId owning template identifier
	 * @param versionLabel version label
	 * @param storagePath untrusted relative storage path
	 * @return created draft
	 */
	TemplateVersionView createVersion(UUID templateId, String versionLabel, String storagePath);

	/**
	 * Validates and publishes a version permanently.
	 * @param versionId version identifier
	 * @param actor publishing instructor
	 * @return immutable published version
	 */
	TemplateVersionView publish(UUID versionId, String actor);

}
