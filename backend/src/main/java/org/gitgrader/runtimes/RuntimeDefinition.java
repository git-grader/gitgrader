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

package org.gitgrader.runtimes;

import org.jspecify.annotations.Nullable;

/**
 * Values used to create or replace a runtime definition.
 *
 * @param runtimeKey stable runtime key
 * @param displayName human-readable name
 * @param image container image repository
 * @param tag documentary image tag
 * @param imageDigest immutable OCI image digest
 * @param installCommand optional dependency installation command
 * @param testCommand command that runs tests
 * @param reportFormat test report format
 * @param enabled whether assignments may select the runtime
 */
public record RuntimeDefinition(String runtimeKey, String displayName, String image, String tag, String imageDigest,
		@Nullable String installCommand, String testCommand, ReportFormat reportFormat, boolean enabled) {
}
