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
 * Web concerns shared by every REST endpoint.
 *
 * <p>
 * Holds the pieces that would otherwise be duplicated in each module's controllers: the
 * RFC 9457 error mapping, the OpenAPI description, the single-page-application forward,
 * and the public metadata endpoint that tells the frontend what this deployment is
 * called.
 *
 * <p>
 * Deliberately thin. Anything with business meaning belongs in the owning module; this
 * module only knows about HTTP.
 */
@org.springframework.modulith.ApplicationModule(displayName = "Web API",
		allowedDependencies = { "identity", "sshkeys", "reports", "courses", "assignments", "submissions", "grading",
				"security" })
@org.jspecify.annotations.NullMarked
package org.gitgrader.api;
