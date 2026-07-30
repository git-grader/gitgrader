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

/** Lifecycle state of a student profile. */
public enum StudentStatus {

	/** Registered through self service but not instructor-verified. */
	SELF_REGISTERED,
	/** Verified by an instructor. */
	VERIFIED_BY_INSTRUCTOR,
	/** Temporarily prevented from submitting. */
	SUSPENDED,
	/** Permanently retained as historical data. */
	ARCHIVED;

	/**
	 * Whether a profile in this state may push work.
	 *
	 * <p>
	 * {@code SELF_REGISTERED} is allowed by default: self-registration is the normal path
	 * and blocking it until an instructor clicks a button would make the platform
	 * unusable at the start of a semester. Deployments that need the stricter rule set
	 * {@code app.registration.require-instructor-verification}, which is applied by the
	 * caller through {@link #canSubmit(boolean)}.
	 * @return true when the profile is not suspended or archived
	 */
	public boolean canSubmit() {
		return canSubmit(false);
	}

	/**
	 * Whether a profile in this state may push work under the configured strictness.
	 * @param requireInstructorVerification when true, only an instructor-verified profile
	 * may push
	 * @return true when the profile may push
	 */
	public boolean canSubmit(boolean requireInstructorVerification) {
		if (this == SUSPENDED || this == ARCHIVED) {
			return false;
		}
		return !requireInstructorVerification || this == VERIFIED_BY_INSTRUCTOR;
	}

}
