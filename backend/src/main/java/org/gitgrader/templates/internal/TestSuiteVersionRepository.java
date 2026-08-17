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

package org.gitgrader.templates.internal;

import java.util.List;
import java.util.UUID;

import org.gitgrader.templates.PublishedTestSuiteVersionView;
import org.gitgrader.templates.domain.TestSuiteVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Persists hidden test-suite versions. */
interface TestSuiteVersionRepository extends JpaRepository<TestSuiteVersion, UUID> {

	/**
	 * Lists versions for a test suite.
	 * @param suiteId suite identifier
	 * @return versions in creation order
	 */
	List<TestSuiteVersion> findBySuiteIdOrderByCreatedAtAsc(UUID suiteId);

	/**
	 * Lists every published version across all suites, with its suite's name and counts.
	 *
	 * <p>
	 * Only the metadata recorded at publication is selected, so this never touches the
	 * hidden tests themselves.
	 * @return published versions ordered by suite name, then by publication order
	 */
	@Query("""
			select new org.gitgrader.templates.PublishedTestSuiteVersionView(
					v.id, s.name, v.versionLabel, v.hiddenTestCount, v.publicTestCount)
			from TestSuiteVersion v, TestSuite s
			where v.suiteId = s.id and v.publishedAt is not null
			order by s.name asc, v.createdAt asc
			""")
	List<PublishedTestSuiteVersionView> findPublished();

}
