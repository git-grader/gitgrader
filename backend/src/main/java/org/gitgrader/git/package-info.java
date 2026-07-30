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
 * The Git endpoint: an embedded SSH server, repository hosting and push admission.
 *
 * <p>
 * Identity comes from the SSH public key used for the transport, and the module then
 * independently checks that every newly pushed commit carries a valid SSHSIG signature
 * made by a key belonging to the same student.
 *
 * <p>
 * A valid signature proves that the commit was signed by a key registered to this
 * student. It is deliberately not presented as proof of unaided authorship.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Git",
		allowedDependencies = { "identity", "sshkeys", "courses", "assignments", "templates", "submissions", "grading",
				"security" })
@org.jspecify.annotations.NullMarked
package org.gitgrader.git;
