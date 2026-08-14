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

package org.gitgrader.registration.domain;

import org.gitgrader.audit.AuditProperties;
import org.gitgrader.audit.ClientAddressHasher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RegistrationAttemptTest {

	@Test
	void storesOnlyHashes() {
		ClientAddressHasher hasher = new ClientAddressHasher(
				new AuditProperties("secret-key", java.time.Duration.ofDays(1)));
		String ip = "1.2.3.4";
		String studentNum = "s12345";
		String email = "john@example.com";

		RegistrationAttempt attempt = new RegistrationAttempt(UUID.randomUUID(), Instant.now(), hasher.hash(ip),
				"ACCEPTED", null, hasher.hash(studentNum), hasher.hash(email));

		assertThat(attempt.getIpHash()).isNotEqualTo(ip);
		assertThat(attempt.getStudentNumberHash()).isNotEqualTo(studentNum);
		assertThat(attempt.getEmailHash()).isNotEqualTo(email);

		assertThat(attempt.getIpHash()).hasSize(32);
	}

}
