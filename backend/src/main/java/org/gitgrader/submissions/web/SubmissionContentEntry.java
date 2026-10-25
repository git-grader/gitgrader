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

package org.gitgrader.submissions.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import org.jspecify.annotations.Nullable;

/**
 * One entry in a submission file tree listing.
 *
 * @param path absolute path from the repository root, e.g. {@code src/string-utils.js}
 * @param name leaf name, e.g. {@code string-utils.js}
 * @param type {@code "file"} or {@code "dir"}
 * @param size bytes for a file; absent for a directory
 * @param binary whether the file appears to be binary (no content is returned in that
 * case)
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmissionContentEntry(String path, String name, String type, @Nullable Long size,
		@Nullable Boolean binary) {

}
