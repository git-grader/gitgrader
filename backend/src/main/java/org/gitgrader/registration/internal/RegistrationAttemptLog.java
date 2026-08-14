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

package org.gitgrader.registration.internal;

import java.time.Instant;
import java.util.UUID;

import org.gitgrader.registration.domain.RegistrationAttempt;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Writes the record of one registration attempt.
 *
 * <p>
 * Every attempt worth recording is one that ends by throwing: a flood that hit the rate
 * limit, or a student number or address already taken. Written on the registration
 * transaction, those rows were rolled back with it and never existed, so a table indexed
 * by address and hour, carrying a {@code RATE_LIMITED} outcome and described as being
 * there to investigate a flood, could only ever hold the registrations that succeeded.
 * The one moment an operator would go looking is the one moment nothing had been kept.
 *
 * <p>
 * A separate transaction survives that rollback. This is the same arrangement the audit
 * service uses, and for the same reason: a record of what happened has to outlive the
 * failure it describes.
 */
@Component
class RegistrationAttemptLog {

	private static final Logger logger = LoggerFactory.getLogger(RegistrationAttemptLog.class);

	private final RegistrationAttemptRepository attempts;

	RegistrationAttemptLog(RegistrationAttemptRepository attempts) {
		this.attempts = attempts;
	}

	/**
	 * Records one attempt in a transaction of its own.
	 * @param at when the attempt was made
	 * @param ipHash keyed hash of the client address
	 * @param outcome one of the outcomes the schema allows
	 * @param reason why it ended that way, when there is more to say
	 * @param studentNumberHash keyed hash of the submitted student number
	 * @param emailHash keyed hash of the submitted e-mail address
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	void record(Instant at, String ipHash, String outcome, @Nullable String reason, String studentNumberHash,
			String emailHash) {
		try {
			this.attempts.save(new RegistrationAttempt(UUID.randomUUID(), at, ipHash, outcome, reason,
					studentNumberHash, emailHash));
		}
		catch (RuntimeException ex) {
			// Losing the record of an attempt must not turn a refused registration into
			// a server error, which would tell a student the service is broken.
			logger.error("Unable to record a {} registration attempt", outcome, ex);
		}
	}

}
