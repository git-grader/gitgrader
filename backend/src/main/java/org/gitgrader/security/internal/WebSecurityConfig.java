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

import org.gitgrader.configuration.SecurityProperties;
import org.gitgrader.security.RateLimiter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

/**
 * Main security configuration.
 */
@Configuration(proxyBeanMethods = false)
@EnableWebSecurity
public class WebSecurityConfig {

	/** Where Spring Security's form login posts; the rate limit is applied to it. */
	private static final String LOGIN_PROCESSING_URL = "/login";

	private final SecurityProperties properties;

	private final RateLimiter rateLimiter;

	public WebSecurityConfig(SecurityProperties properties, RateLimiter rateLimiter) {
		this.properties = properties;
		this.rateLimiter = rateLimiter;
	}

	@Bean
	@Order(0)
	public SecurityFilterChain resultFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/result/**")
			.authorizeHttpRequests((authz) -> authz.anyRequest().permitAll())
			// Safe here: /result/** is read-only and has no state-changing operation to
			// forge. Every chain that accepts a POST keeps CSRF on with a cookie token.
			.csrf((csrf) -> csrf.disable())
			.headers((headers) -> headers.xssProtection((xss) -> xss.disable())
				.contentSecurityPolicy((csp) -> csp.policyDirectives(this.properties.resultContentSecurityPolicy()))
				.contentTypeOptions((contentType) -> {
				}) // nosniff
				.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
				.frameOptions((frame) -> frame.deny())
				.addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter("X-Robots-Tag",
						"noindex, nofollow")));
		return http.build();
	}

	@Bean
	@Order(1)
	public SecurityFilterChain publicFilterChain(HttpSecurity http) throws Exception {
		// The shell and its assets are as public as the pages that need them:
		// registration
		// and a result link are both reached without signing in, and neither can render
		// if the bundle behind them is gated. The bundler emits to /assets, so listing
		// only /js and /css left every public route redirecting to the sign-in page.
		http.securityMatcher("/register/**", "/api/v1/registration/**", "/api/v1/results/**", "/api/v1/meta",
				"/index.html", "/assets/**", "/favicon.ico", "/apple-touch-icon.png", "/*.svg", "/*.png", "/css/**",
				"/js/**", "/images/**", "/actuator/health/**", "/actuator/info")
			.authorizeHttpRequests((authz) -> authz.anyRequest().permitAll())
			.csrf((csrf) -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
			.headers((headers) -> headers.xssProtection((xss) -> xss.disable())
				.contentSecurityPolicy((csp) -> csp.policyDirectives(this.properties.contentSecurityPolicy()))
				.contentTypeOptions((contentType) -> {
				})
				.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
				.frameOptions((frame) -> frame.deny()));
		return http.build();
	}

	@Bean
	@Order(2)
	public SecurityFilterChain apiFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/api/v1/**")
			.authorizeHttpRequests((authz) -> authz.anyRequest().authenticated())
			// Answer 401 rather than redirecting to the sign-in page. The SPA calls this
			// chain with fetch(), which follows a 302 transparently and would then parse
			// the HTML login page as JSON - turning "your session expired" into an
			// unreadable parse error.
			.exceptionHandling(
					(exceptions) -> exceptions.authenticationEntryPoint(new ProblemDetailAuthenticationEntryPoint()))
			.csrf((csrf) -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
			.sessionManagement((session) -> session.sessionFixation((fixation) -> fixation.migrateSession()))
			.headers((headers) -> headers
				.contentSecurityPolicy((csp) -> csp.policyDirectives(this.properties.contentSecurityPolicy()))
				.contentTypeOptions((contentType) -> {
				}) // Defaults to nosniff
				.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
				.frameOptions((frame) -> frame.deny()));
		return http.build();
	}

	@Bean
	@Order(3)
	public SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
		http.securityMatcher("/actuator/**")
			.authorizeHttpRequests((authz) -> authz.anyRequest().hasRole("ADMIN"))
			// Safe here: this chain authenticates with HTTP Basic, not with an ambient
			// session cookie, so a cross-site request cannot borrow the caller's
			// credentials. Only read-only endpoints are exposed (see application.yaml).
			.csrf((csrf) -> csrf.disable())
			.httpBasic((basic) -> {
			});

		return http.build();
	}

	@Bean
	public SecurityFilterChain defaultFilterChain(HttpSecurity http) throws Exception {
		http.authorizeHttpRequests((authz) -> authz.anyRequest().authenticated())
			// Naming the page stops Spring Security generating one of its own, which it
			// serves at this same path and which would otherwise be the first thing
			// anyone signing in sees, unstyled and unbranded.
			.formLogin((form) -> form.loginPage(LOGIN_PROCESSING_URL).permitAll())
			.addFilterBefore(new LoginRateLimitFilter(this.rateLimiter, LOGIN_PROCESSING_URL),
					UsernamePasswordAuthenticationFilter.class)
			.logout((logout) -> logout.logoutUrl("/logout")
				.invalidateHttpSession(true)
				.deleteCookies(this.properties.session().cookieName()))
			.sessionManagement((session) -> session.sessionFixation((fixation) -> fixation.migrateSession()))
			.csrf((csrf) -> csrf.csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
				.csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
			.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
			.headers((headers) -> headers
				.contentSecurityPolicy((csp) -> csp.policyDirectives(this.properties.contentSecurityPolicy()))
				.contentTypeOptions((contentType) -> {
				})
				.referrerPolicy((referrer) -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
				.frameOptions((frame) -> frame.deny()));

		return http.build();
	}

}
