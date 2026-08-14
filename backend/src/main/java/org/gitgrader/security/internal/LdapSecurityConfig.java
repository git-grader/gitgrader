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
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.ldap.DefaultSpringSecurityContextSource;
import org.springframework.security.ldap.authentication.BindAuthenticator;
import org.springframework.security.ldap.authentication.LdapAuthenticationProvider;
import org.springframework.security.ldap.search.FilterBasedLdapUserSearch;
import org.springframework.security.ldap.userdetails.DefaultLdapAuthoritiesPopulator;

/**
 * Configures LDAP authentication when active.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "security.ldap", name = "enabled", havingValue = "true")
public class LdapSecurityConfig {

	@Bean
	public LdapAuthenticationProvider ldapAuthenticationProvider(SecurityProperties properties) {
		SecurityProperties.Ldap ldapProps = properties.ldap();

		DefaultSpringSecurityContextSource contextSource = new DefaultSpringSecurityContextSource(
				ldapProps.url() + "/" + ldapProps.baseDn());
		if (ldapProps.managerDn() != null && !ldapProps.managerDn().isBlank()) {
			contextSource.setUserDn(ldapProps.managerDn());
			contextSource.setPassword(ldapProps.managerPassword());
		}
		contextSource.afterPropertiesSet();

		FilterBasedLdapUserSearch userSearch = new FilterBasedLdapUserSearch(ldapProps.userSearchBase(),
				ldapProps.userSearchFilter(), contextSource);

		BindAuthenticator authenticator = new BindAuthenticator(contextSource);
		authenticator.setUserSearch(userSearch);

		DefaultLdapAuthoritiesPopulator groups = new DefaultLdapAuthoritiesPopulator(contextSource,
				ldapProps.groupSearchBase());
		groups.setGroupSearchFilter(ldapProps.groupSearchFilter());
		// The group name is compared as the directory spells it, so neither a prefix nor
		// a case change may be applied before GroupRoleMapper sees it.
		groups.setRolePrefix("");
		groups.setConvertToUpperCase(false);

		return new LdapAuthenticationProvider(authenticator,
				new GroupRoleMapper(groups, ldapProps.instructorGroup(), ldapProps.adminGroup()));
	}

}
