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
import jakarta.persistence.Version;
import org.jspecify.annotations.Nullable;

/** A named suite stored exclusively under the hidden-test root. */
@Entity
@Table(name = "test_suites")
public class TestSuite {

	@Id
	private UUID id;

	@Column(name = "suite_key", nullable = false)
	private String suiteKey;

	@Column(nullable = false)
	private String name;

	@Column
	private @Nullable String description;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected TestSuite() {
	}

	/**
	 * Creates a hidden test suite.
	 * @param suiteKey stable key
	 * @param name display name
	 * @param description optional description
	 * @param clock application clock
	 */
	public TestSuite(String suiteKey, String name, @Nullable String description, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.suiteKey = suiteKey;
		this.name = name;
		this.description = description;
		this.createdAt = now;
		this.updatedAt = now;
	}

	/**
	 * Returns the suite identifier.
	 * @return suite identifier
	 */
	public UUID id() {
		return this.id;
	}

}
