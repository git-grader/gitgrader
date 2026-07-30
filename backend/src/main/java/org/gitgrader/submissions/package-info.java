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
 * Immutable record of everything that was ever accepted from a student.
 *
 * <p>
 * A submission is append only. Re-grading creates an additional grading run and leaves
 * the original run intact, so a result can always be reproduced with the template
 * version, test suite version and runtime image digest recorded at the time.
 *
 * <p>
 * Publishes {@code SubmissionRecorded}; the {@code grading} module consumes it. The
 * dependency deliberately points one way so that persistence never waits on grading.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Submissions",
		allowedDependencies = { "identity", "assignments", "courses", "sshkeys" })
@org.jspecify.annotations.NullMarked
package org.gitgrader.submissions;
