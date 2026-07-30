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

import java.util.UUID;

/** Creates and changes student profiles through their legal lifecycle operations. */
public interface StudentRegistry {

	/**
	 * Creates a self-registered student.
	 * @param registration registration values
	 * @return created profile
	 */
	StudentView register(StudentRegistration registration);

	/**
	 * Verifies a self-registered student.
	 * @param studentId profile identifier
	 * @param actor verifying actor
	 * @return updated profile
	 */
	StudentView verify(UUID studentId, Actor actor);

	/**
	 * Suspends a profile.
	 * @param studentId profile identifier
	 * @param reason suspension reason
	 * @param actor suspending actor
	 * @return updated profile
	 */
	StudentView suspend(UUID studentId, String reason, Actor actor);

	/**
	 * Archives a profile.
	 * @param studentId profile identifier
	 * @return updated profile
	 */
	StudentView archive(UUID studentId);

	/**
	 * Replaces personal fields with stable placeholders while preserving the profile id.
	 * @param studentId profile identifier
	 * @return anonymized profile
	 */
	StudentView anonymize(UUID studentId);

}
