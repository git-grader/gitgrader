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

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Provides read-only access to hidden test-suite versions. */
public interface TestSuiteCatalog {

	/**
	 * Finds a hidden test-suite summary without content details.
	 * @param id suite identifier
	 * @return matching suite, if present
	 */
	Optional<TestSuiteView> findTestSuite(UUID id);

	/**
	 * Lists hidden test suites without exposing their content.
	 * @param pageable requested page and ordering
	 * @return matching suites
	 */
	Page<TestSuiteView> findAll(Pageable pageable);

	/**
	 * Finds a test-suite version.
	 * @param id version identifier
	 * @return matching version, if present
	 */
	Optional<TestSuiteVersionView> findVersion(UUID id);

	/**
	 * Lists versions belonging to a test suite.
	 * @param suiteId test-suite identifier
	 * @return versions in creation order
	 */
	List<TestSuiteVersionView> findVersions(UUID suiteId);

	/**
	 * Lists every published version across all suites, each with its suite's name.
	 * @return published versions ordered by suite name, then by publication order
	 */
	List<PublishedTestSuiteVersionView> findPublishedVersions();

}
