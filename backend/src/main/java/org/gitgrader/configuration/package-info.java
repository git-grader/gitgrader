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

/**
 * Typed, externally bindable configuration for the whole platform.
 *
 * <p>
 * This module owns every value that a self-hosting operator may need to change: the
 * product name, the public URL, the SSH host, storage directories, grading limits and
 * rate limits. Nothing in GitGrader is allowed to hard code a host name, an organization
 * name, a support address or a course identifier - a Checkstyle rule and a PMD rule both
 * fail the build when a literal URL or an organization name appears in source.
 *
 * <p>
 * Declared as a shared module, so every other module may read configuration without
 * declaring an explicit dependency.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Configuration", allowedDependencies = {})
@org.jspecify.annotations.NullMarked
package org.gitgrader.configuration;
