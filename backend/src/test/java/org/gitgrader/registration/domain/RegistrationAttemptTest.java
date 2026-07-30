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
