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
 * Public, unauthenticated self-service registration for students.
 *
 * <p>
 * Because the endpoint is public it is also the most exposed surface of the platform. The
 * module owns the abuse controls: per-IP and global rate limiting, configurable
 * registration windows, uniqueness checks on student id, e-mail and key fingerprint, and
 * the ability to close registration globally or per course.
 *
 * <p>
 * A self-registered profile is explicitly <em>not</em> a verified identity. It starts as
 * {@code SELF_REGISTERED} and only an instructor can raise it to
 * {@code VERIFIED_BY_INSTRUCTOR}.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Registration",
		allowedDependencies = { "identity", "sshkeys", "courses", "security" })
@org.jspecify.annotations.NullMarked
package org.gitgrader.registration;
