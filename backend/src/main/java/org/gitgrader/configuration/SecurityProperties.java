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

package org.gitgrader.configuration;

import java.time.Duration;
import java.util.List;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Authentication, authorization and abuse control.
 *
 * @param ldap directory settings for instructor and administrator sign-in
 * @param localAccounts development-only accounts, refused under the production profile
 * @param rateLimits limits applied to public and semi-public endpoints
 * @param session web session hardening
 * @param contentSecurityPolicy CSP delivered with the application shell
 * @param resultContentSecurityPolicy stricter CSP delivered with the public result page
 */
@ConfigurationProperties(prefix = "security")
@Validated
public record SecurityProperties(

		@DefaultValue Ldap ldap,

		@DefaultValue LocalAccounts localAccounts,

		@DefaultValue RateLimits rateLimits,

		@DefaultValue Session session,

		@DefaultValue("default-src 'self'; object-src 'none'; base-uri 'self'; "
				+ "frame-ancestors 'none'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
				+ "font-src 'self' data:; connect-src 'self'; form-action 'self'") @NotBlank String contentSecurityPolicy,

		@DefaultValue("default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
				+ "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; "
				+ "base-uri 'none'; form-action 'none'; frame-ancestors 'none'") @NotBlank String resultContentSecurityPolicy) {

	/**
	 * LDAP directory settings.
	 *
	 * <p>
	 * The whole block is external configuration; no directory structure is compiled in.
	 * Credentials are never written to a log at any level.
	 *
	 * @param enabled whether LDAP authentication is active
	 * @param url directory URL; {@code ldaps://} is strongly preferred
	 * @param baseDn directory root
	 * @param managerDn bind account for searches, empty for anonymous bind
	 * @param managerPassword bind account password
	 * @param userSearchBase subtree holding people, relative to {@code baseDn}
	 * @param userSearchFilter filter used to locate a person, {@code {0}} is the login
	 * @param groupSearchBase subtree holding groups, relative to {@code baseDn}
	 * @param groupSearchFilter filter used to locate a person's groups
	 * @param instructorGroup group whose members receive the instructor role
	 * @param adminGroup group whose members receive the administrator role
	 * @param verifyCertificate validate the directory's TLS certificate; disabling this
	 * is refused under the production profile
	 * @param referral JNDI referral handling
	 */
	public record Ldap(

			@DefaultValue("false") boolean enabled,

			@DefaultValue("ldap://localhost:389") String url,

			@DefaultValue("") String baseDn,

			@DefaultValue("") String managerDn,

			@DefaultValue("") String managerPassword,

			@DefaultValue("ou=people") String userSearchBase,

			@DefaultValue("(uid={0})") String userSearchFilter,

			@DefaultValue("ou=groups") String groupSearchBase,

			@DefaultValue("(member={0})") String groupSearchFilter,

			@DefaultValue("gitgrader-instructors") String instructorGroup,

			@DefaultValue("gitgrader-admins") String adminGroup,

			@DefaultValue("true") boolean verifyCertificate,

			@DefaultValue("follow") String referral) {

		/**
		 * Whether the configured URL uses TLS.
		 * @return true when the directory is contacted over {@code ldaps://}
		 */
		public boolean isSecure() {
			return this.url != null && this.url.startsWith("ldaps://");
		}
	}

	/**
	 * Development-only accounts.
	 *
	 * <p>
	 * These exist so that the platform can be run and demonstrated without a directory. A
	 * startup check refuses to boot when they are enabled together with the production
	 * profile, because a forgotten development account is a back door.
	 *
	 * @param enabled master switch
	 * @param accounts the accounts to create in memory
	 */
	public record LocalAccounts(

			@DefaultValue("false") boolean enabled,

			@DefaultValue List<Account> accounts) {

		/**
		 * A single in-memory development account.
		 *
		 * @param username login name
		 * @param password plain text password, development only
		 * @param displayName name shown in the UI
		 * @param roles granted roles, without the {@code ROLE_} prefix
		 */
		public record Account(String username, String password, String displayName, List<String> roles) {
		}
	}

	/**
	 * Rate limits for endpoints reachable without authentication.
	 *
	 * @param registrationPerHourPerIp registration attempts allowed from one address
	 * @param registrationPerHourGlobal registration attempts allowed in total, which caps
	 * a distributed flood
	 * @param resultLookupPerMinutePerIp result page requests allowed from one address,
	 * which makes token guessing pointless on top of the token entropy
	 * @param loginPerMinutePerIp sign-in attempts allowed from one address
	 * @param sshAuthPerMinutePerIp SSH authentication attempts allowed from one address
	 * @param submissionsPerHourPerAssignment accepted pushes allowed from one student on
	 * one assignment in a rolling hour; counted in the database, so it holds across
	 * restarts and across instances in a way an in-memory bucket cannot
	 * @param submissionsPerHourPerStudent accepted pushes allowed from one student across
	 * every assignment in a rolling hour
	 * @param blockDuration how long a source is refused after exhausting a limit
	 */
	public record RateLimits(

			@DefaultValue("5") @Min(1) int registrationPerHourPerIp,

			@DefaultValue("200") @Min(1) int registrationPerHourGlobal,

			@DefaultValue("60") @Min(1) int resultLookupPerMinutePerIp,

			@DefaultValue("10") @Min(1) int loginPerMinutePerIp,

			@DefaultValue("30") @Min(1) int sshAuthPerMinutePerIp,

			@DefaultValue("20") @Min(1) int submissionsPerHourPerAssignment,

			@DefaultValue("60") @Min(1) int submissionsPerHourPerStudent,

			@DefaultValue("15m") Duration blockDuration) {
	}

	/**
	 * Web session hardening.
	 *
	 * @param timeout idle session lifetime
	 * @param cookieName name of the session cookie
	 * @param secureCookie mark the cookie {@code Secure}; must stay true behind TLS
	 * @param sameSite {@code SameSite} attribute of the session cookie
	 */
	public record Session(

			@DefaultValue("8h") Duration timeout,

			@DefaultValue("GITGRADER_SESSION") @NotBlank String cookieName,

			@DefaultValue("true") boolean secureCookie,

			@DefaultValue("Lax") @NotBlank String sameSite) {
	}

}
