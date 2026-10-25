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
 * The content of one file at the graded commit of a submission. Binary files carry no
 * content; they are reported as binary so the interface can show a placeholder.
 *
 * @param path absolute path from the repository root
 * @param content the file's text, decoded UTF-8; absent for a binary file
 * @param binary whether the content is binary
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SubmissionFileContent(String path, @Nullable String content, @Nullable Boolean binary) {

}
