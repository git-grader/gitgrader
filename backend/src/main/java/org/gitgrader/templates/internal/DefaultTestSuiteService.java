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

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityNotFoundException;
import org.gitgrader.configuration.StorageProperties;
import org.gitgrader.templates.TestSuiteAdministration;
import org.gitgrader.templates.TestSuiteCatalog;
import org.gitgrader.templates.TestSuiteVersionView;
import org.gitgrader.templates.domain.TestSuite;
import org.gitgrader.templates.domain.TestSuiteVersion;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.transaction.annotation.Transactional;

/** Default hidden test-suite catalog and administration implementation. */
@Service
@Transactional
public class DefaultTestSuiteService implements TestSuiteCatalog, TestSuiteAdministration {

	private final TestSuiteRepository suites;

	private final TestSuiteVersionRepository versions;

	private final StorageProperties storage;

	private final Clock clock;

	DefaultTestSuiteService(TestSuiteRepository suites, TestSuiteVersionRepository versions, StorageProperties storage,
			Clock clock) {
		this.suites = suites;
		this.versions = versions;
		this.storage = storage;
		this.clock = clock;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<org.gitgrader.templates.TestSuiteView> findTestSuite(UUID id) {
		return this.suites.findById(id).map(TestSuite::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<org.gitgrader.templates.TestSuiteView> findAll(Pageable pageable) {
		return this.suites.findAll(pageable).map(TestSuite::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<TestSuiteVersionView> findVersion(UUID id) {
		return this.versions.findById(id).map(TestSuiteVersion::toView);
	}

	@Override
	@Transactional(readOnly = true)
	public List<TestSuiteVersionView> findVersions(UUID suiteId) {
		return this.versions.findBySuiteIdOrderByCreatedAtAsc(suiteId).stream().map(TestSuiteVersion::toView).toList();
	}

	@Override
	public UUID createTestSuite(String suiteKey, String name, @Nullable String description) {
		return this.suites.save(new TestSuite(suiteKey, name, description, this.clock)).id();
	}

	@Override
	public TestSuiteVersionView createVersion(UUID suiteId, String versionLabel, String storagePath) {
		requireSuite(suiteId);
		StorageProperties.resolveInside(this.storage.tests(), storagePath);
		return this.versions.save(new TestSuiteVersion(suiteId, versionLabel, storagePath, this.clock)).toView();
	}

	@Override
	public TestSuiteVersionView publish(UUID versionId, String actor, int hiddenTestCount, int publicTestCount) {
		TestSuiteVersion version = requireVersion(versionId);
		Path directory = StorageProperties.resolveInside(this.storage.tests(), version.storagePath());
		ContentSnapshot snapshot = ContentHasher.snapshot(directory);
		version.publish(snapshot.hash(), hiddenTestCount, publicTestCount, actor, this.clock);
		return this.versions.save(version).toView();
	}

	private void requireSuite(UUID id) {
		if (!this.suites.existsById(id)) {
			throw new EntityNotFoundException("Test suite not found: " + id);
		}
	}

	private TestSuiteVersion requireVersion(UUID id) {
		return this.versions.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Test-suite version not found: " + id));
	}

}
