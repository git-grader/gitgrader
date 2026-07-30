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

package org.gitgrader.git.domain;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.gitgrader.git.RepositoryStatus;
import org.jspecify.annotations.Nullable;

/**
 * One student's bare Git repository for one assignment.
 *
 * <p>
 * {@code repositoryPath} is the authoritative link between what an SSH client asks for
 * and who is allowed to have it. The transport resolves a request by looking the path up
 * here rather than by parsing it, so an attacker cannot construct a path that
 * accidentally grants access to somebody else's work.
 */
@Entity
@Table(name = "repositories")
public class RepositoryRecord {

	@Id
	private UUID id;

	@Column(name = "assignment_id", nullable = false, updatable = false)
	private UUID assignmentId;

	@Column(name = "student_id", nullable = false, updatable = false)
	private UUID studentId;

	@Column(name = "repository_path", nullable = false, updatable = false)
	private String repositoryPath;

	@Column(name = "template_version_id")
	private @Nullable UUID templateVersionId;

	@Column(name = "provisioned_at")
	private @Nullable Instant provisionedAt;

	@Column(name = "last_push_at")
	private @Nullable Instant lastPushAt;

	@Column(name = "push_count", nullable = false)
	private int pushCount;

	@Column(name = "size_bytes", nullable = false)
	private long sizeBytes;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private RepositoryStatus status;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Version
	private long version;

	protected RepositoryRecord() {
		// Required by JPA.
	}

	public RepositoryRecord(UUID assignmentId, UUID studentId, String repositoryPath, Clock clock) {
		Instant now = Instant.now(clock);
		this.id = UUID.randomUUID();
		this.assignmentId = assignmentId;
		this.studentId = studentId;
		this.repositoryPath = repositoryPath;
		this.status = RepositoryStatus.PENDING;
		this.createdAt = now;
		this.updatedAt = now;
	}

	public UUID id() {
		return this.id;
	}

	public UUID assignmentId() {
		return this.assignmentId;
	}

	public UUID studentId() {
		return this.studentId;
	}

	public String repositoryPath() {
		return this.repositoryPath;
	}

	public @Nullable UUID templateVersionId() {
		return this.templateVersionId;
	}

	public @Nullable Instant provisionedAt() {
		return this.provisionedAt;
	}

	public @Nullable Instant lastPushAt() {
		return this.lastPushAt;
	}

	public int pushCount() {
		return this.pushCount;
	}

	public RepositoryStatus status() {
		return this.status;
	}

	/**
	 * Whether this repository currently accepts pushes.
	 * @return true when the repository itself is not blocking writes
	 */
	public boolean acceptsPushes() {
		return this.status.acceptsPushes();
	}

	/**
	 * Marks the bare repository as created and pinned to a template version.
	 * @param templateVersion the template version the repository was seeded from
	 * @param clock the application clock
	 */
	public void markProvisioned(@Nullable UUID templateVersion, Clock clock) {
		this.templateVersionId = templateVersion;
		this.provisionedAt = Instant.now(clock);
		this.status = RepositoryStatus.READY;
		this.updatedAt = this.provisionedAt;
	}

	/**
	 * Records that a push was accepted.
	 * @param clock the application clock
	 */
	public void recordPush(Clock clock) {
		this.lastPushAt = Instant.now(clock);
		this.pushCount++;
		this.updatedAt = this.lastPushAt;
	}

	/**
	 * Records the on-disk size after a push.
	 * @param bytes measured size
	 * @param clock the application clock
	 */
	public void recordSize(long bytes, Clock clock) {
		this.sizeBytes = bytes;
		this.updatedAt = Instant.now(clock);
	}

}
