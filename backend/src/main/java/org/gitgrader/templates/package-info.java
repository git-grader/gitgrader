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
 * Immutable, versioned starter projects handed to students.
 *
 * <p>
 * A template version is content addressed and never mutated in place. Publishing a new
 * version has no effect on repositories that were already provisioned, which is what
 * keeps an in-flight assignment stable across a platform upgrade.
 *
 * <p>
 * Templates contain only material a student is allowed to see. Hidden tests live in the
 * {@code grading} module and are physically stored outside any template.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Templates", allowedDependencies = {})
@org.jspecify.annotations.NullMarked
package org.gitgrader.templates;
