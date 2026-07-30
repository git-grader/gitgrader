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
 * Authentication and authorization for the web surface.
 *
 * <p>
 * Instructors and administrators authenticate against LDAP; group membership is mapped
 * onto roles through configuration. Local development accounts exist only under an
 * explicitly activated profile and refuse to start under the production profile.
 *
 * <p>
 * Also owns the unguessable result token: issued once in plain text, stored only as a
 * hash, and resolved with a constant-time comparison.
 *
 * <p>
 * Every authorization decision is enforced server side. The frontend hides controls for
 * convenience only and is never the sole gate.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Security",
		allowedDependencies = { "identity", "submissions" })
@org.jspecify.annotations.NullMarked
package org.gitgrader.security;
