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
 * Versioned execution environments available to assignments.
 *
 * <p>
 * A runtime is pinned to an immutable image digest, never to a moving tag such as
 * {@code latest}. Adding a language or a new language version is a data change, not a
 * code change, and existing assignments never migrate implicitly.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Runtimes", allowedDependencies = {})
@org.jspecify.annotations.NullMarked
package org.gitgrader.runtimes;
