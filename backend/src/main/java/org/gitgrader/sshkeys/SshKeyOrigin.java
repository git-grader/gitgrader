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

package org.gitgrader.sshkeys;

/**
 * How a key came to be registered.
 *
 * <p>
 * Recorded because the provenance of a key is exactly what an instructor needs when a
 * submission is disputed: a key an instructor uploaded on a student's behalf carries a
 * different weight from one the student added themselves over an already trusted SSH
 * connection.
 */
public enum SshKeyOrigin {

	/** Supplied during public self-service registration. */
	REGISTRATION,

	/**
	 * Added by the student over SSH, authenticated with a key they already held.
	 *
	 * <p>
	 * The strongest provenance available without a directory: possession of an existing
	 * registered private key was proven before the new key was accepted.
	 */
	SELF_SERVICE_SSH,

	/** Entered by an instructor, typically to restore access after a lost key. */
	INSTRUCTOR,

	/** Entered by an administrator during maintenance or data repair. */
	ADMIN

}
