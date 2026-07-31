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

package org.gitgrader.templates.web;

/** An invalid or unreadable coursework ZIP upload. */
public class ArchiveUploadException extends RuntimeException {

	/**
	 * Creates a client-safe archive rejection.
	 * @param message public rejection reason
	 */
	public ArchiveUploadException(String message) {
		super(message);
	}

	ArchiveUploadException(String message, Throwable cause) {
		super(message, cause);
	}

}
