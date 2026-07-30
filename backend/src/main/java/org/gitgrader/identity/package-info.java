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
 * People known to the platform and the roles they hold.
 *
 * <p>
 * Students are self registered and never receive a web password. Instructors and
 * administrators are authenticated externally through LDAP and have no row in the student
 * table. The {@code Actor} abstraction is what other modules use to answer "who is doing
 * this" without depending on the authentication mechanism.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Identity", allowedDependencies = {})
@org.jspecify.annotations.NullMarked
package org.gitgrader.identity;
