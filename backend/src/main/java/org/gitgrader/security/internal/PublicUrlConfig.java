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

import java.net.InetAddress;
import java.net.URI;

import org.gitgrader.configuration.AppProperties;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;

/**
 * Refuses to publish result links over plain HTTP.
 *
 * <p>
 * The token in a result URL is the entire credential for that result: anyone holding the
 * link reads the student's marks. Over {@code http://} it crosses the network in the
 * clear, and the application prints it into the student's terminal at the end of every
 * push, so an operator who never revisits {@code app.public-url} hands out one bearer
 * token per submission over an unencrypted channel.
 *
 * <p>
 * Loopback is exempt: that is the demo, and the documentation says so.
 */
@Configuration(proxyBeanMethods = false)
public class PublicUrlConfig {

	@Bean
	public PublicUrlGuard publicUrlGuard(AppProperties properties, Environment environment) {
		return new PublicUrlGuard(properties, environment);
	}

	/**
	 * Guard bean that stops a production instance handing out result links over HTTP.
	 */
	public static class PublicUrlGuard implements InitializingBean {

		private final AppProperties properties;

		private final Environment environment;

		public PublicUrlGuard(AppProperties properties, Environment environment) {
			this.properties = properties;
			this.environment = environment;
		}

		@Override
		public void afterPropertiesSet() {
			URI url = this.properties.publicUrl();
			if (!this.environment.acceptsProfiles(Profiles.of("production")) || isSafe(url)) {
				return;
			}
			throw new IllegalStateException("app.public-url must be an https:// URL under the production profile, "
					+ "but it is " + url + ". Every result link carries the token that reads a student's marks.");
		}

		private static boolean isSafe(URI url) {
			return "https".equalsIgnoreCase(url.getScheme()) || isLoopback(url.getHost());
		}

		private static boolean isLoopback(String host) {
			if (host == null) {
				return false;
			}
			if ("localhost".equalsIgnoreCase(host)) {
				return true;
			}
			// Parsed as a literal rather than resolved: this runs during startup, and a
			// real hostname must not cost a DNS round trip, let alone a DNS timeout on an
			// air-gapped host. Reading the literal also covers all of 127.0.0.0/8 and ::1
			// instead of the three spellings anyone thinks to write down.
			String address = host.startsWith("[") && host.endsWith("]") ? host.substring(1, host.length() - 1) : host;
			try {
				return InetAddress.ofLiteral(address).isLoopbackAddress();
			}
			catch (IllegalArgumentException notALiteral) {
				return false;
			}
		}

	}

}
