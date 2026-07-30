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

package org.gitgrader.templates.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.gitgrader.templates.TestSuiteVersionView;
import org.jspecify.annotations.Nullable;

/** A content-addressed hidden test-suite version frozen on publication. */
@Entity
@Table(name = "test_suite_versions")
public class TestSuiteVersion {

	@Id
	private UUID id;

	@Column(name = "suite_id", nullable = false)
	private UUID suiteId;

	@Column(name = "version_label", nullable = false)
	private String versionLabel;

	@Column(name = "storage_path", nullable = false)
	private String storagePath;

	@Column(name = "content_hash", nullable = false)
	private String contentHash;

	@Column(name = "hidden_test_count", nullable = false)
	private int hiddenTestCount;

	@Column(name = "public_test_count", nullable = false)
	private int publicTestCount;

	@Column(name = "published_at")
	private @Nullable Instant publishedAt;

	@Column(name = "published_by")
	private @Nullable String publishedBy;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	protected TestSuiteVersion() {
	}

	/**
	 * Creates a publication draft.
	 * @param suiteId owning suite
	 * @param versionLabel version label
	 * @param storagePath path relative to hidden-test storage
	 * @param clock application clock
	 */
	public TestSuiteVersion(UUID suiteId, String versionLabel, String storagePath, Clock clock) {
		this.id = UUID.randomUUID();
		this.suiteId = suiteId;
		this.versionLabel = versionLabel;
		this.storagePath = storagePath;
		this.contentHash = "";
		this.createdAt = Instant.now(clock);
	}

	/**
	 * Changes the draft storage path.
	 * @param storagePath new relative path
	 * @throws IllegalStateException after publication
	 */
	public void changeStoragePath(String storagePath) {
		requireDraft();
		this.storagePath = storagePath;
	}

	/**
	 * Publishes the current bytes and permanently freezes the version.
	 * @param contentHash computed SHA-256 hash
	 * @param hiddenTestCount number of hidden tests
	 * @param publicTestCount number of public tests
	 * @param actor publishing instructor
	 * @param clock application clock
	 */
	public void publish(String contentHash, int hiddenTestCount, int publicTestCount, String actor, Clock clock) {
		requireDraft();
		this.contentHash = contentHash;
		this.hiddenTestCount = hiddenTestCount;
		this.publicTestCount = publicTestCount;
		this.publishedAt = Instant.now(clock);
		this.publishedBy = actor;
	}

	/**
	 * Returns the relative storage path.
	 * @return storage path
	 */
	public String storagePath() {
		return this.storagePath;
	}

	/**
	 * Converts this entity to its public read model.
	 * @return test-suite version view
	 */
	public TestSuiteVersionView toView() {
		return new TestSuiteVersionView(this.id, this.suiteId, this.versionLabel, this.storagePath, this.contentHash,
				this.hiddenTestCount, this.publicTestCount, this.publishedAt, this.publishedBy, this.createdAt);
	}

	private void requireDraft() {
		if (this.publishedAt != null) {
			throw new IllegalStateException("A published test-suite version is immutable");
		}
	}

}
