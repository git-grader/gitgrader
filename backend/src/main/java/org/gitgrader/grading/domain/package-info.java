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
 * Persistence entities describing grading work: the queue entry, the run, and the
 * per-test outcome.
 *
 * <p>
 * These are the entities rather than the public vocabulary of the grading module. A
 * {@code GradingRun} here is a stored row with its own lifecycle and status, distinct
 * from the run the module reports to callers; the mapping between the two lives in the
 * module's own package, so nothing here depends on how a run is presented.
 */
@NullMarked
package org.gitgrader.grading.domain;

import org.jspecify.annotations.NullMarked;
