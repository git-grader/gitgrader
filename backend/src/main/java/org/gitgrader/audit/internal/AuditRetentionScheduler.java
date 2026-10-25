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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically enforces the configured audit event retention period.
 */
@Component
public class AuditRetentionScheduler {

	private static final Logger logger = LoggerFactory.getLogger(AuditRetentionScheduler.class);

	private final DefaultAuditService auditService;

	public AuditRetentionScheduler(DefaultAuditService auditService) {
		this.auditService = auditService;
	}

	/** Runs once a day, one minute after startup and then every 24 hours. */
	@Scheduled(initialDelayString = "PT1M", fixedRate = 86_400_000)
	public void purgeExpiredAuditEvents() {
		try {
			this.auditService.purgeExpiredEvents();
		}
		catch (RuntimeException ex) {
			// The scheduler stops invoking a task that throws, so a failed purge would
			// silently end enforcement for the life of the process. Log and wait for
			// the next tick instead.
			logger.error("Audit retention purge failed", ex);
		}
	}

}
