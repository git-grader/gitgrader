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

package org.gitgrader.git.internal;

import java.security.PublicKey;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.sshd.common.AttributeRepository.AttributeKey;
import org.apache.sshd.server.session.ServerSession;
import org.gitgrader.identity.StudentDirectory;
import org.gitgrader.identity.StudentView;
import org.gitgrader.security.RateLimiter;
import org.gitgrader.sshkeys.SshKeyParser;
import org.gitgrader.sshkeys.SshKeyRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.stubbing.Answer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The per-IP allowance has to survive a client that offers more than one key.
 */
class StudentKeyAuthenticatorRateLimitTest {

	private static final String FINGERPRINT = "SHA256:AAAA";

	@Test
	@DisplayName("charges one connection once, however many keys it offers")
	void chargesPerSessionRatherThanPerOfferedKey() {
		// An SSH client offers every key it holds until one is accepted. Charging each
		// offer separately meant a student with three keys spent three of their site's
		// allowance to make one push, and a class behind a single campus address locked
		// itself out long before all of it had connected.
		AtomicInteger charged = new AtomicInteger();
		StudentKeyAuthenticator authenticator = authenticatorCharging(charged, true);
		ServerSession session = sessionWithAttributes();

		authenticator.authenticate("git", key(), session);
		authenticator.authenticate("git", key(), session);
		authenticator.authenticate("git", key(), session);

		assertThat(charged.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("still refuses a connection once the source has spent its allowance")
	void refusesWhenTheAllowanceIsSpent() {
		AtomicInteger charged = new AtomicInteger();
		StudentKeyAuthenticator authenticator = authenticatorCharging(charged, false);

		boolean accepted = authenticator.authenticate("git", key(), sessionWithAttributes());

		assertThat(accepted).isFalse();
		assertThat(charged.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("charges each connection separately")
	void chargesEverySessionSeparately() {
		AtomicInteger charged = new AtomicInteger();
		StudentKeyAuthenticator authenticator = authenticatorCharging(charged, true);

		authenticator.authenticate("git", key(), sessionWithAttributes());
		authenticator.authenticate("git", key(), sessionWithAttributes());

		assertThat(charged.get()).isEqualTo(2);
	}

	private static StudentKeyAuthenticator authenticatorCharging(AtomicInteger charged, boolean allowed) {
		RateLimiter rateLimiter = mock(RateLimiter.class);
		when(rateLimiter.tryConsumeSshAuthPerIp(any())).thenAnswer((invocation) -> {
			charged.incrementAndGet();
			return allowed;
		});

		SshKeyParser parser = mock(SshKeyParser.class);
		when(parser.fingerprintOf(any())).thenReturn(FINGERPRINT);

		SshKeyRegistry keys = mock(SshKeyRegistry.class);
		when(keys.findUsableByFingerprint(anyString())).thenReturn(Optional.empty());

		StudentDirectory students = mock(StudentDirectory.class);
		when(students.findById(any(UUID.class))).thenReturn(Optional.<StudentView>empty());

		return new StudentKeyAuthenticator(keys, students, parser, rateLimiter);
	}

	/**
	 * A session that remembers what is set on it, which is the whole point of the test.
	 * @return a stub server session with working attribute storage
	 */
	private static ServerSession sessionWithAttributes() {
		Map<AttributeKey<?>, Object> attributes = new HashMap<>();
		ServerSession session = mock(ServerSession.class);
		when(session.getAttribute(any())).thenAnswer((invocation) -> attributes.get(invocation.getArgument(0)));
		Answer<Object> remember = (invocation) -> attributes.put(invocation.getArgument(0), invocation.getArgument(1));
		when(session.setAttribute(any(), any())).thenAnswer(remember);
		return session;
	}

	private static PublicKey key() {
		return mock(PublicKey.class);
	}

}
