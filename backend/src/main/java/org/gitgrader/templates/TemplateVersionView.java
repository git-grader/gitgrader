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

import java.time.Instant;
import java.util.UUID;

import org.jspecify.annotations.Nullable;

/**
 * An immutable published template version or a mutable publication draft.
 *
 * @param id version identifier
 * @param templateId owning template identifier
 * @param versionLabel instructor-facing version label
 * @param storagePath path relative to template storage
 * @param contentHash SHA-256 content hash
 * @param fileCount number of files
 * @param totalBytes total file bytes
 * @param publishedAt publication timestamp
 * @param publishedBy publishing instructor
 * @param createdAt creation timestamp
 */
public record TemplateVersionView(UUID id, UUID templateId, String versionLabel, String storagePath, String contentHash,
		int fileCount, long totalBytes, @Nullable Instant publishedAt, @Nullable String publishedBy,
		Instant createdAt) {
}
