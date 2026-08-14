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
 * Read-only aggregation and export of course progress.
 *
 * <p>
 * Keeps completion rate (fully solved mandatory assignments over mandatory assignments)
 * strictly separate from points rate (points earned over points available); conflating
 * the two is the most common way such a report misleads.
 *
 * <p>
 * Depends on grading to read the score a run actually recorded. Without it the only
 * percentage available here is inferred from a submission's status, which collapses every
 * outcome to nothing or everything: a student who scored 70 against an 80 threshold is
 * FAILED, and so was reported as zero points rather than seventy percent of them. The
 * dependency is read-only and reaches no further than {@code GradingResultQuery}.
 *
 * <p>
 * This module never writes business data.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Reports",
		allowedDependencies = { "identity", "courses", "assignments", "submissions", "grading" })
@org.jspecify.annotations.NullMarked
package org.gitgrader.reports;
