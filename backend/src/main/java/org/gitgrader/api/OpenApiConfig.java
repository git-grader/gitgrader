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

package org.gitgrader.api;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.gitgrader.configuration.AppProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.info.BuildProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

import java.util.List;

/**
 * Describes the REST API for tooling and for the generated documentation.
 *
 * <p>
 * Every user-visible string is read from configuration rather than written here, for the
 * same reason the rest of the product does it: a fork should be able to rebrand the
 * generated documentation without editing Java. The build enforces that - a literal
 * organization name in source fails Checkstyle.
 */
@Configuration
@EnableMethodSecurity
public class OpenApiConfig {

	private final AppProperties app;

	private final @Nullable BuildProperties buildProperties;

	public OpenApiConfig(AppProperties app, @Nullable BuildProperties buildProperties) {
		this.app = app;
		this.buildProperties = buildProperties;
	}

	/**
	 * Builds the OpenAPI document served at {@code /api/v1/openapi}.
	 * @return the API description
	 */
	@Bean
	public OpenAPI gitGraderOpenApi() {
		return new OpenAPI()
			.info(new Info().title(this.app.name() + " API")
				.version((this.buildProperties != null) ? this.buildProperties.getVersion() : "dev")
				.description(description())
				.contact(new Contact().email(this.app.supportEmail()))
				.license(new License().name("Apache License 2.0").url("https://www.apache.org/licenses/LICENSE-2.0")))
			.servers(List.of(new Server().url(this.app.baseUrl()).description("This deployment")));
	}

	/**
	 * Assembles the API description from configured values.
	 *
	 * <p>
	 * Concatenated rather than formatted: {@code String.formatted} is
	 * {@code String.format} with the default locale, which this project bans outright
	 * because it makes output depend on the ambient JVM settings.
	 * @return the description text
	 */
	private String description() {
		return "REST API of " + this.app.name() + ", operated by " + this.app.organizationName() + ".\n\n"
				+ "Errors follow RFC 9457 and are returned as application/problem+json. "
				+ "Collection endpoints are paged and accept page, size and sort parameters.\n\n"
				+ "Authentication is a session cookie obtained through the instructor sign-in; "
				+ "students never authenticate against this API. The public result endpoint is "
				+ "authorised solely by an unguessable token.";
	}

}
