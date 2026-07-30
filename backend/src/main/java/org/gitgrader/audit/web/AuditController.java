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

package org.gitgrader.audit.web;

import java.time.Instant;

import org.gitgrader.audit.AuditEntry;
import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditQuery;
import org.gitgrader.audit.AuditRecord.ActorType;
import org.gitgrader.audit.AuditService;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Serves administrator-only audit searches. */
@RestController
@RequestMapping("/api/v1/audit")
@PreAuthorize("hasRole('ADMIN')")
public class AuditController {

	private final AuditService audit;

	public AuditController(AuditService audit) {
		this.audit = audit;
	}

	@GetMapping
	public Page<AuditEntry> list(@RequestParam(required = false) @Nullable AuditEventType eventType,
			@RequestParam(required = false) @Nullable ActorType actorType,
			@RequestParam(required = false) @Nullable Instant from,
			@RequestParam(required = false) @Nullable Instant to, Pageable pageable) {
		return this.audit.find(new AuditQuery(eventType, actorType, from, to, null), pageable);
	}

}
