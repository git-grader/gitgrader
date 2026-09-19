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

package org.gitgrader.security.internal;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditOutcome;
import org.gitgrader.audit.AuditProperties;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditRecord.ActorType;
import org.gitgrader.audit.AuditService;
import org.gitgrader.audit.ClientAddressHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Tests that interactive sign-in, rejection, and sign-out land in the audit trail with a
 * hashed source address and never with a password.
 */
class LoginAuditRecorderTest {

	private static final String CLIENT_IP = "203.0.113.7";

	// Fixed so the source-address hashes in the assertions are deterministic.
	private static final String HASH_KEY = "patch-test-key";

	private AuditService auditService;

	private ClientAddressHasher hasher;

	private LoginAuditRecorder recorder;

	@BeforeEach
	void setUp() {
		this.auditService = mock(AuditService.class);
		this.hasher = new ClientAddressHasher(new AuditProperties(HASH_KEY, Duration.ofDays(1)));
		this.recorder = new LoginAuditRecorder(this.auditService, this.hasher);
	}

	@Test
	@DisplayName("records a successful sign-in with the granted role and the hashed caller address")
	void recordsSuccessfulSignInAsAdmin() {
		MockHttpServletResponse response = new MockHttpServletResponse();
		this.recorder.successHandler()
			.onAuthenticationSuccess(requestWithForwardedForHeader(), response,
					authentication("bamc", "ROLE_ADMIN", "ROLE_INSTRUCTOR"));

		AuditRecord record = captured();
		assertThat(record.type()).isEqualTo(AuditEventType.LOGIN_SUCCEEDED);
		assertThat(record.outcome()).isEqualTo(AuditOutcome.SUCCESS);
		assertThat(record.actorType()).isEqualTo(ActorType.ADMIN);
		assertThat(record.actorId()).isEqualTo("bamc");
		assertThat(record.actorName()).isEqualTo("bamc");
		// The address is the peer, never a header the caller chose (see ClientAddress).
		assertThat(record.sourceIpHash()).isEqualTo(this.hasher.hash(CLIENT_IP));
		assertThat(record.detail()).containsEntry("roles", List.of("ROLE_ADMIN", "ROLE_INSTRUCTOR"));
		// Recording must not swallow the default behaviour: back to the landing page.
		assertThat(response.getRedirectedUrl()).isEqualTo("/");
	}

	@Test
	@DisplayName("classifies an instructor-only sign-in as an instructor")
	void recordsSuccessfulSignInAsInstructor() {
		this.recorder.successHandler()
			.onAuthenticationSuccess(request(), new MockHttpServletResponse(),
					authentication("lee", "ROLE_INSTRUCTOR"));

		AuditRecord record = captured();
		assertThat(record.actorType()).isEqualTo(ActorType.INSTRUCTOR);
		assertThat(record.actorName()).isEqualTo("lee");
	}

	@Test
	@DisplayName("records an account in neither role without mislabelling it")
	void recordsRolelessSignInAsSystem() {
		this.recorder.successHandler()
			.onAuthenticationSuccess(request(), new MockHttpServletResponse(), authentication("ghost"));

		AuditRecord record = captured();
		assertThat(record.type()).isEqualTo(AuditEventType.LOGIN_SUCCEEDED);
		assertThat(record.actorType()).isEqualTo(ActorType.SYSTEM);
		assertThat(record.actorName()).isEqualTo("ghost");
		assertThat(record.detail()).containsEntry("roles", List.of());
	}

	@Test
	@DisplayName("records a rejected sign-in with the attempted account and the rejection reason")
	void recordsRejectedSignIn() {
		AuthenticationException failure = new BadCredentialsException("bad credentials");
		failure.setAuthentication(authentication("attacker"));

		MockHttpServletResponse response = new MockHttpServletResponse();
		this.recorder.failureHandler().onAuthenticationFailure(request(), response, failure);

		AuditRecord record = captured();
		assertThat(record.type()).isEqualTo(AuditEventType.LOGIN_FAILED);
		assertThat(record.outcome()).isEqualTo(AuditOutcome.FAILURE);
		assertThat(record.actorType()).isEqualTo(ActorType.ANONYMOUS);
		assertThat(record.actorName()).isEqualTo("attacker");
		assertThat(record.sourceIpHash()).isEqualTo(this.hasher.hash(CLIENT_IP));
		assertThat(record.detail()).containsEntry("reason", "BadCredentialsException");
		// Recording must not swallow the default behaviour: back to the sign-in page.
		assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
	}

	@Test
	@DisplayName("still records a rejection when the attempted account cannot be read")
	void recordsRejectedSignInWithoutAttemptedAccount() {
		this.recorder.failureHandler()
			.onAuthenticationFailure(request(), new MockHttpServletResponse(), new BadCredentialsException("boom"));

		AuditRecord record = captured();
		assertThat(record.type()).isEqualTo(AuditEventType.LOGIN_FAILED);
		assertThat(record.actorName()).isNull();
		assertThat(record.sourceIpHash()).isEqualTo(this.hasher.hash(CLIENT_IP));
	}

	@Test
	@DisplayName("records sign-out, and stays silent when there is nothing to sign out of")
	void recordsSignOutOnlyForARealSession() {
		MockHttpServletResponse response = new MockHttpServletResponse();
		this.recorder.logoutHandler().logout(request(), response, authentication("bamc", "ROLE_ADMIN"));

		assertThat(captured().type()).isEqualTo(AuditEventType.LOGOUT);

		this.recorder.logoutHandler().logout(request(), response, null);
		verify(this.auditService, times(1)).record(any());
	}

	private AuditRecord captured() {
		ArgumentCaptor<AuditRecord> captor = ArgumentCaptor.forClass(AuditRecord.class);
		verify(this.auditService).record(captor.capture());
		return captor.getValue();
	}

	private static Authentication authentication(String username, String... roles) {
		List<SimpleGrantedAuthority> authorities = Arrays.stream(roles).map(SimpleGrantedAuthority::new).toList();
		return new UsernamePasswordAuthenticationToken(username, "not-this-password", authorities);
	}

	private static MockHttpServletRequest requestWithForwardedForHeader() {
		MockHttpServletRequest request = request();
		request.addHeader("X-Forwarded-For", "198.51.100.9");
		return request;
	}

	private static MockHttpServletRequest request() {
		MockHttpServletRequest request = new MockHttpServletRequest();
		request.setRemoteAddr(CLIENT_IP);
		return request;
	}

}
