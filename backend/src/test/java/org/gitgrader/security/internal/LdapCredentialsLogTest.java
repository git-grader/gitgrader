package org.gitgrader.security.internal;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class LdapCredentialsLogTest {

	@Test
	void managerPasswordNotLogged() {
		LdapSecurityConfig config = new LdapSecurityConfig();
		assertThat(config.toString()).doesNotContain("secretPassword");
	}

}
