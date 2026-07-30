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

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.criteria.Predicate;

import org.gitgrader.audit.AuditEntry;
import org.gitgrader.audit.AuditQuery;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditService;
import org.gitgrader.audit.domain.AuditEventEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Default database-backed audit service. */
@Service
public class DefaultAuditService implements AuditService {

	private static final Logger logger = LoggerFactory.getLogger(DefaultAuditService.class);

	private final AuditEventRepository repository;

	private final Clock clock;

	DefaultAuditService(AuditEventRepository repository, Clock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Override
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(AuditRecord record) {
		try {
			this.repository.save(new AuditEventEntity(record, Instant.now(this.clock)));
		}
		catch (RuntimeException exception) {
			logger.error("Unable to persist audit event of type {}", record.type(), exception);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Page<AuditEntry> find(AuditQuery query, Pageable pageable) {
		return this.repository.findAll(specification(query), pageable).map(AuditEventEntity::toEntry);
	}

	private static Specification<AuditEventEntity> specification(AuditQuery query) {
		return (root, criteriaQuery, builder) -> {
			List<Predicate> predicates = new ArrayList<>();
			if (query.eventType() != null) {
				predicates.add(builder.equal(root.get("eventType"), query.eventType()));
			}
			if (query.actorType() != null) {
				predicates.add(builder.equal(root.get("actorType"), query.actorType()));
			}
			if (query.from() != null) {
				predicates.add(builder.greaterThanOrEqualTo(root.get("occurredAt"), query.from()));
			}
			if (query.to() != null) {
				predicates.add(builder.lessThanOrEqualTo(root.get("occurredAt"), query.to()));
			}
			if (query.subjectId() != null) {
				predicates.add(builder.equal(root.get("subjectId"), query.subjectId()));
			}
			return builder.and(predicates.toArray(Predicate[]::new));
		};
	}

}
