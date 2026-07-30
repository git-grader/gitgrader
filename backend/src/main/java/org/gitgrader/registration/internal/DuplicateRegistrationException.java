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

package org.gitgrader.registration.internal;

/**
 * Raised when a student number, e-mail address or key is already registered.
 *
 * <p>
 * The message returned to the caller is deliberately generic. Confirming which identifier
 * already exists would turn the public form into an enumeration oracle.
 */
public class DuplicateRegistrationException extends RuntimeException {

	public DuplicateRegistrationException(String message) {
		super(message);
	}

	public DuplicateRegistrationException(String message, Throwable cause) {
		super(message, cause);
	}

}
