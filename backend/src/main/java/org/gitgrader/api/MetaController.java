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

import org.gitgrader.configuration.AppProperties;
import org.gitgrader.configuration.GitProperties;
import org.jspecify.annotations.Nullable;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the frontend what this deployment is called.
 *
 * <p>
 * Public and unauthenticated on purpose: the registration page and the result page both
 * render before anyone has signed in, and both need the product name, the support address
 * and the SSH host to say anything useful.
 *
 * <p>
 * This endpoint is what makes the UI organization neutral. Nothing in the frontend
 * hardcodes a name, a domain or a support address; it all comes from here, which is why a
 * fork only has to change configuration.
 */
@RestController
@RequestMapping("/api/v1/meta")
public class MetaController {

	private final AppProperties app;

	private final GitProperties git;

	private final @Nullable BuildProperties buildProperties;

	public MetaController(AppProperties app, GitProperties git, @Nullable BuildProperties buildProperties) {
		this.app = app;
		this.git = git;
		this.buildProperties = buildProperties;
	}

	/**
	 * Returns the deployment's public identity.
	 * @return metadata safe to expose without authentication
	 */
	@GetMapping
	public MetaResponse meta() {
		return new MetaResponse(this.app.name(), this.app.organizationName(), this.app.supportEmail(),
				this.app.documentationUrl().toString(), this.app.baseUrl(), this.git.sshHost(), this.git.sshPort(),
				this.app.registration().enabled(),
				(this.buildProperties != null) ? this.buildProperties.getVersion() : "dev");
	}

	/**
	 * The publicly visible identity of this deployment.
	 *
	 * <p>
	 * Contains no personal data and nothing an unauthenticated caller could not already
	 * infer from the login page, so it is safe to serve to anyone.
	 *
	 * @param name product name shown throughout the UI
	 * @param organizationName who operates this instance
	 * @param supportEmail where a student should write when something breaks
	 * @param documentationUrl end-user documentation
	 * @param publicUrl externally reachable base URL
	 * @param sshHost host students clone from
	 * @param sshPort port students clone from
	 * @param registrationEnabled whether the public registration form is open
	 * @param version running build version
	 */
	public record MetaResponse(String name, String organizationName, String supportEmail, String documentationUrl,
			String publicUrl, String sshHost, int sshPort, boolean registrationEnabled, String version) {
	}

}
