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

package org.gitgrader.runtimes.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.gitgrader.runtimes.ReportFormat;
import org.gitgrader.runtimes.RuntimeView;
import org.jspecify.annotations.Nullable;

/**
 * A configured grading runtime pinned to immutable container content.
 *
 * <p>
 * A moving tag would silently change how an old submission grades and break
 * reproducibility. The tag is retained only as documentation; sandbox execution always
 * uses {@link #pinnedReference()}.
 * </p>
 */
@Entity
@Table(name = "runtimes")
public class RuntimeDefinition {

	private static final Pattern IMAGE_DIGEST = Pattern.compile("^sha256:[a-f0-9]{64}$");

	@Id
	private UUID id;

	@Column(name = "runtime_key", nullable = false)
	private String runtimeKey;

	@Column(name = "display_name", nullable = false)
	private String displayName;

	@Column(nullable = false)
	private String image;

	@Column(nullable = false)
	private String tag;

	@Column(name = "image_digest", nullable = false)
	private String imageDigest;

	@Column(name = "install_command")
	private @Nullable String installCommand;

	@Column(name = "test_command", nullable = false)
	private String testCommand;

	@Enumerated(EnumType.STRING)
	@Column(name = "report_format", nullable = false)
	private ReportFormat reportFormat;

	@Column(nullable = false)
	private boolean enabled;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	@Column(nullable = false)
	private long version;

	protected RuntimeDefinition() {
		// Required by JPA.
	}

	/**
	 * Creates a validated runtime.
	 * @param definition runtime values
	 * @param clock application clock
	 */
	public RuntimeDefinition(org.gitgrader.runtimes.RuntimeDefinition definition, Clock clock) {
		validate(definition);
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.createdAt = now;
		apply(definition, now);
	}

	/**
	 * Replaces the runtime metadata after applying pinning rules.
	 * @param definition replacement values
	 * @param clock application clock
	 */
	public void update(org.gitgrader.runtimes.RuntimeDefinition definition, Clock clock) {
		validate(definition);
		apply(definition, Instant.now(clock));
	}

	/**
	 * Returns the exact image reference used by the sandbox.
	 * @return digest-pinned image reference
	 */
	public String pinnedReference() {
		return this.image + "@" + this.imageDigest;
	}

	/**
	 * Converts this entity to its public read model.
	 * @return runtime view
	 */
	public RuntimeView toView() {
		return new RuntimeView(this.id, this.runtimeKey, this.displayName, this.image, this.tag, this.imageDigest,
				this.installCommand, this.testCommand, this.reportFormat, this.enabled, this.createdAt, this.updatedAt);
	}

	private void apply(org.gitgrader.runtimes.RuntimeDefinition definition, Instant updatedAt) {
		this.runtimeKey = definition.runtimeKey();
		this.displayName = definition.displayName();
		this.image = definition.image();
		this.tag = definition.tag();
		this.imageDigest = definition.imageDigest();
		this.installCommand = definition.installCommand();
		this.testCommand = definition.testCommand();
		this.reportFormat = definition.reportFormat();
		this.enabled = definition.enabled();
		this.updatedAt = updatedAt;
	}

	private static void validate(org.gitgrader.runtimes.RuntimeDefinition definition) {
		if (!IMAGE_DIGEST.matcher(definition.imageDigest()).matches()) {
			throw new IllegalArgumentException(
					"Image digest must be sha256 followed by 64 lowercase hexadecimal characters");
		}
		if ("latest".equalsIgnoreCase(definition.tag())) {
			throw new IllegalArgumentException("The moving tag 'latest' is forbidden; pin a reproducible image digest");
		}
	}

}
