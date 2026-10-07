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
 * HTTP endpoints for project templates, test suites and the materials a student may see.
 *
 * <p>
 * Two pieces here are security-relevant rather than merely plumbing. Archive extraction
 * refuses a traversing or oversized entry name, because an entry name is
 * attacker-controlled and becomes a filesystem path. And what a student may read is
 * decided per endpoint: the published-materials route serves the student-visible subset,
 * never the hidden suite.
 */
@NullMarked
package org.gitgrader.templates.web;

import org.jspecify.annotations.NullMarked;
