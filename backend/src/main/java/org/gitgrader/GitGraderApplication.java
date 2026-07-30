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

package org.gitgrader;

import java.time.Clock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.annotation.Bean;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * GitGrader application entry point.
 *
 * <p>
 * GitGrader is deployed as a modular monolith. Every top level package below
 * {@code org.gitgrader} is a Spring Modulith application module with an explicitly
 * declared dependency allow-list; {@code ModularityTests} fails the build when a module
 * reaches across a boundary it did not declare.
 *
 * <p>
 * Cycles between {@code git}, {@code submissions} and {@code grading} are broken with
 * Spring Modulith application events. Those events are persisted through the JPA event
 * publication registry, so an in-flight grading job survives a restart, which is what
 * lets the platform run without an external message broker.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
@EnableScheduling
@Modulithic(systemName = "GitGrader", sharedModules = { "configuration", "audit" })
public class GitGraderApplication {

	public static void main(String[] args) {
		SpringApplication.run(GitGraderApplication.class, args);
	}

	/**
	 * The single source of time for the whole application.
	 *
	 * <p>
	 * Deadlines, extensions, token expiry and the server side push receive timestamp are
	 * all legally meaningful, so no component is allowed to call {@code Instant.now()}
	 * directly. That rule is mechanically enforced by the forbidden-apis signatures in
	 * {@code config/forbiddenapis/signatures.txt}, which makes every time dependent rule
	 * deterministically testable.
	 * @return a UTC clock
	 */
	@Bean
	public Clock clock() {
		return Clock.systemUTC();
	}

}
