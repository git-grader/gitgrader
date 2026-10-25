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

package org.gitgrader.audit.internal;

import java.time.Instant;
import java.util.UUID;

import org.gitgrader.audit.domain.AuditEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Persists audit events. */
interface AuditEventRepository
		extends JpaRepository<AuditEventEntity, UUID>, JpaSpecificationExecutor<AuditEventEntity> {

	/**
	 * Deletes all events that occurred before the given cutoff.
	 * @param cutoff events strictly before this instant are deleted
	 * @return the number of rows removed
	 */
	@Modifying
	@Query("DELETE FROM AuditEventEntity e WHERE e.occurredAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") Instant cutoff);

}
