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

package org.gitgrader.templates;

import java.util.UUID;

import org.jspecify.annotations.Nullable;

/** Creates hidden test suites and permanently publishes their versions. */
public interface TestSuiteAdministration {

	/**
	 * Creates a test suite.
	 * @param suiteKey stable suite key
	 * @param name display name
	 * @param description optional description
	 * @return suite identifier
	 */
	UUID createTestSuite(String suiteKey, String name, @Nullable String description);

	/**
	 * Creates a version draft relative to hidden-test storage.
	 * @param suiteId owning suite identifier
	 * @param versionLabel version label
	 * @param storagePath untrusted relative storage path
	 * @return created draft
	 */
	TestSuiteVersionView createVersion(UUID suiteId, String versionLabel, String storagePath);

	/**
	 * Publishes a test-suite version permanently.
	 * @param versionId version identifier
	 * @param actor publishing instructor
	 * @param hiddenTestCount hidden test count
	 * @param publicTestCount public test count
	 * @return immutable published version
	 */
	TestSuiteVersionView publish(UUID versionId, String actor, int hiddenTestCount, int publicTestCount);

}
