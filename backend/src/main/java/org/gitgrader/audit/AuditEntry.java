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

package org.gitgrader.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.gitgrader.audit.AuditRecord.ActorType;
import org.gitgrader.audit.AuditRecord.AuditOutcome;
import org.gitgrader.audit.AuditRecord.AuditSeverity;
import org.jspecify.annotations.Nullable;

/**
 * Read-only representation of a persisted audit event.
 *
 * @param id event identifier
 * @param occurredAt server time at which the event was recorded
 * @param eventType type of event
 * @param severity attention level
 * @param actorType kind of actor
 * @param actorId stable actor identifier
 * @param actorName human-readable actor name
 * @param subjectType kind of affected object
 * @param subjectId affected object identifier
 * @param courseId owning course
 * @param outcome result of the action
 * @param sourceIpHash keyed source-address hash
 * @param correlationId operation correlation identifier
 * @param detail structured event details
 */
public record AuditEntry(UUID id, Instant occurredAt, AuditEventType eventType, AuditSeverity severity,
		ActorType actorType, @Nullable String actorId, @Nullable String actorName, @Nullable String subjectType,
		@Nullable String subjectId, @Nullable UUID courseId, AuditOutcome outcome, @Nullable String sourceIpHash,
		@Nullable String correlationId, Map<String, Object> detail) {

	public AuditEntry {
		detail = Map.copyOf(detail);
	}

}
