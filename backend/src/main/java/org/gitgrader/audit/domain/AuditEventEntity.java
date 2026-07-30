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

package org.gitgrader.audit.domain;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.gitgrader.audit.AuditEntry;
import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditRecord.ActorType;
import org.gitgrader.audit.AuditRecord.AuditOutcome;
import org.gitgrader.audit.AuditRecord.AuditSeverity;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.jspecify.annotations.Nullable;

/** Persistence representation of an audit event. */
@Entity
@Table(name = "audit_events")
public class AuditEventEntity {

	@Id
	private UUID id;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "event_type", nullable = false)
	private AuditEventType eventType;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuditSeverity severity;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor_type", nullable = false)
	private ActorType actorType;

	@Column(name = "actor_id")
	private @Nullable String actorId;

	@Column(name = "actor_name")
	private @Nullable String actorName;

	@Column(name = "subject_type")
	private @Nullable String subjectType;

	@Column(name = "subject_id")
	private @Nullable String subjectId;

	@Column(name = "course_id")
	private @Nullable UUID courseId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private AuditOutcome outcome;

	@Column(name = "source_ip_hash")
	private @Nullable String sourceIpHash;

	@Column(name = "correlation_id")
	private @Nullable String correlationId;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "detail", nullable = false, columnDefinition = "jsonb")
	private Map<String, Object> detail = new HashMap<>();

	protected AuditEventEntity() {
	}

	/**
	 * Creates a persistence row from a public audit record.
	 * @param record event values
	 * @param occurredAt server recording time
	 */
	public AuditEventEntity(AuditRecord record, Instant occurredAt) {
		this.id = UUID.randomUUID();
		this.occurredAt = occurredAt;
		this.eventType = record.type();
		this.severity = record.severity();
		this.actorType = record.actorType();
		this.actorId = record.actorId();
		this.actorName = record.actorName();
		this.subjectType = record.subjectType();
		this.subjectId = record.subjectId();
		this.courseId = record.courseId();
		this.outcome = record.outcome();
		this.sourceIpHash = record.sourceIpHash();
		this.correlationId = record.correlationId();
		this.detail = new HashMap<>(record.detail());
	}

	/**
	 * Converts this row to the module's public read model.
	 * @return immutable audit entry
	 */
	public AuditEntry toEntry() {
		return new AuditEntry(this.id, this.occurredAt, this.eventType, this.severity, this.actorType, this.actorId,
				this.actorName, this.subjectType, this.subjectId, this.courseId, this.outcome, this.sourceIpHash,
				this.correlationId, this.detail);
	}

}
