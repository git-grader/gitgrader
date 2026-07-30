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

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/** Stores and searches audit events without exposing their persistence model. */
public interface AuditService {

	/**
	 * Records an event without propagating persistence failures to the caller.
	 * @param record event to record
	 */
	void record(AuditRecord record);

	/**
	 * Finds audit entries matching the supplied optional filters.
	 * @param query optional filters
	 * @param pageable requested page and ordering
	 * @return matching audit entries
	 */
	Page<AuditEntry> find(AuditQuery query, Pageable pageable);

}
