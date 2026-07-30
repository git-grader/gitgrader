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

package org.gitgrader.identity;

/** Kind of authenticated or system actor performing an operation. */
public enum ActorType {

	/** Student authenticated through an SSH key. */
	STUDENT,
	/** Instructor authenticated through the directory. */
	INSTRUCTOR,
	/** Administrator authenticated through the directory. */
	ADMIN,
	/** Automated platform operation. */
	SYSTEM,
	/** Unauthenticated caller. */
	ANONYMOUS

}
