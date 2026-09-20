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

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.gitgrader.audit.AuditEventType;
import org.gitgrader.audit.AuditRecord;
import org.gitgrader.audit.AuditRecord.ActorType;
import org.gitgrader.audit.AuditService;
import org.gitgrader.audit.ClientAddressHasher;
import org.gitgrader.security.ClientAddress;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.stereotype.Component;

/**
 * Records interactive sign-in and sign-out events in the audit trail.
 *
 * <p>
 * This is deliberately not an {@code ApplicationListener} over Spring Security's
 * authentication events: the actuator chain authenticates with HTTP Basic on every scrape
 * and would flood the trail with {@code LOGIN_SUCCEEDED} rows, and the runner API
 * authenticates with a shared secret rather than a person. Only the interactive
 * form-login and logout entry points of the default chain reach these handlers.
 */
@Component
public class LoginAuditRecorder {

	private static final Logger logger = LoggerFactory.getLogger(LoginAuditRecorder.class);

	/**
	 * Where Spring Security sends a failed sign-in, kept in step with the DSL default.
	 */
	private static final String FAILURE_URL = "/login?error";

	private final AuditService auditService;

	private final ClientAddressHasher addressHasher;

	private final AuthenticationSuccessHandler defaultSuccessHandler = new SavedRequestAwareAuthenticationSuccessHandler();

	private final AuthenticationFailureHandler defaultFailureHandler = new SimpleUrlAuthenticationFailureHandler(
			FAILURE_URL);

	public LoginAuditRecorder(AuditService auditService, ClientAddressHasher addressHasher) {
		this.auditService = auditService;
		this.addressHasher = addressHasher;
	}

	/**
	 * Records a successful sign-in and then keeps Spring Security's normal success
	 * behaviour (continue to the saved request, or fall back to the landing page).
	 * @return a success handler that audits before delegating to the default
	 */
	public AuthenticationSuccessHandler successHandler() {
		return (request, response, authentication) -> {
			recordLoginSucceeded(request, authentication);
			this.defaultSuccessHandler.onAuthenticationSuccess(request, response, authentication);
		};
	}

	/**
	 * Records a rejected sign-in and then keeps the usual redirect back to the sign-in
	 * page with the error marker.
	 * @return a failure handler that audits before delegating to the default
	 */
	public AuthenticationFailureHandler failureHandler() {
		return (request, response, exception) -> {
			recordLoginFailed(request, exception);
			this.defaultFailureHandler.onAuthenticationFailure(request, response, exception);
		};
	}

	/**
	 * Records an ended session.
	 *
	 * <p>
	 * Runs before the default handlers tear the security context down, so the
	 * {@link Authentication} is still populated here.
	 * @return an audit-recording logout handler
	 */
	public LogoutHandler logoutHandler() {
		return (request, response, authentication) -> recordLogout(request, authentication);
	}

	private void recordLoginSucceeded(HttpServletRequest request, Authentication authentication) {
		String username = authentication.getName();
		this.auditService.record(AuditRecord.of(AuditEventType.LOGIN_SUCCEEDED)
			.actor(actorTypeOf(authentication), username, username)
			.sourceIpHash(this.addressHasher.hash(ClientAddress.of(request)))
			.with("roles", rolesOf(authentication))
			.build());
	}

	private void recordLoginFailed(HttpServletRequest request, AuthenticationException exception) {
		Authentication attempted = exception.getAuthenticationRequest();
		String username = attempted != null ? attempted.getName() : null;
		this.auditService.record(AuditRecord.of(AuditEventType.LOGIN_FAILED)
			.failed()
			.actor(ActorType.ANONYMOUS, null, username)
			.sourceIpHash(this.addressHasher.hash(ClientAddress.of(request)))
			.with("reason", exception.getClass().getSimpleName())
			.build());
		logger.debug("Rejected sign-in for '{}': {}", username, exception.getClass().getSimpleName());
	}

	private void recordLogout(HttpServletRequest request, Authentication authentication) {
		if (authentication == null) {
			return;
		}
		String username = authentication.getName();
		this.auditService.record(AuditRecord.of(AuditEventType.LOGOUT)
			.actor(actorTypeOf(authentication), username, username)
			.sourceIpHash(this.addressHasher.hash(ClientAddress.of(request)))
			.build());
	}

	private static ActorType actorTypeOf(Authentication authentication) {
		for (GrantedAuthority authority : authentication.getAuthorities()) {
			if (GroupRoleMapper.ADMIN_ROLE.equals(authority.getAuthority())) {
				return ActorType.ADMIN;
			}
			if (GroupRoleMapper.INSTRUCTOR_ROLE.equals(authority.getAuthority())) {
				return ActorType.INSTRUCTOR;
			}
		}
		// An account in neither directory role authenticates with no authorities (see
		// GroupRoleMapper) and is still a real principal; SYSTEM is the closest category
		// the shared module offers, and the actor name keeps the account identifiable.
		logger.debug("Signed-in account '{}' has no GitGrader role; recorded as SYSTEM", authentication.getName());
		return ActorType.SYSTEM;
	}

	private static List<String> rolesOf(Authentication authentication) {
		return authentication.getAuthorities().stream().map(GrantedAuthority::getAuthority).toList();
	}

}
