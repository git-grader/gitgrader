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

import java.net.URI;
import java.time.Duration;
import java.time.ZoneId;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Product level identity and branding of this deployment.
 *
 * <p>
 * Every user visible name, address and link is bound here. No module is permitted to
 * embed an organization name, a support address or a public host name in source: the
 * Checkstyle {@code RegexpSingleline} organization rule and the PMD
 * {@code NoHardcodedPublicUrl} rule both fail the build if one appears.
 *
 * <p>
 * Renaming the product for a fork is therefore a one line change in configuration.
 *
 * @param name product name shown in the UI, in push feedback and in e-mail
 * @param publicUrl externally reachable base URL; every generated result link is built
 * from this value
 * @param supportEmail address shown to students when something goes wrong
 * @param organizationName operator of this instance, shown in the footer and in exports
 * @param documentationUrl where the UI links for end user documentation
 * @param defaultTimezone zone used to render dates when neither course nor assignment
 * pins one
 * @param dataDirectory root of all persistent state that is not in the database
 * @param registration self-service registration switches
 * @param resultTokens lifetime and format of unguessable result links
 */
@ConfigurationProperties(prefix = "app")
@Validated
public record AppProperties(

		@DefaultValue("GitGrader") @NotBlank String name,

		@DefaultValue("http://localhost:8080") @NotNull URI publicUrl,

		@DefaultValue("support@example.org") @Email String supportEmail,

		@DefaultValue("Example Organization") @NotBlank String organizationName,

		@DefaultValue("https://github.com/git-grader/gitgrader") @NotNull URI documentationUrl,

		@DefaultValue("UTC") @NotNull ZoneId defaultTimezone,

		@DefaultValue("/data") @NotBlank String dataDirectory,

		@DefaultValue Registration registration,

		@DefaultValue ResultTokens resultTokens) {

	/**
	 * Absolute, normalised base URL without a trailing slash.
	 *
	 * <p>
	 * Used to build result links and clone instructions so that they are correct behind a
	 * reverse proxy and in a subpath deployment.
	 * @return the public base URL as a string, never ending in {@code /}
	 */
	public String baseUrl() {
		String value = this.publicUrl.toString();
		return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
	}

	/**
	 * Builds an absolute, publicly reachable link to a submission result.
	 * @param token the plain result token handed to the student
	 * @return absolute result URL
	 */
	public String resultUrl(String token) {
		return baseUrl() + "/result/" + token;
	}

	/**
	 * Self-service registration switches.
	 *
	 * @param enabled master switch; when false the public endpoint returns 403 and the
	 * registration page renders a closed notice
	 * @param requireInstructorVerification when true a self-registered student cannot
	 * push until an instructor raises the profile to {@code VERIFIED_BY_INSTRUCTOR}
	 * @param maxKeysPerStudent ceiling on simultaneously active SSH keys per student
	 */
	public record Registration(

			@DefaultValue("true") boolean enabled,

			@DefaultValue("false") boolean requireInstructorVerification,

			@DefaultValue("5") int maxKeysPerStudent) {
	}

	/**
	 * Lifetime and format of the unguessable result links.
	 *
	 * @param entropyBits raw entropy per token; 128 is the documented floor and the
	 * binding refuses to start below it
	 * @param timeToLive how long a link stays valid; {@code PT0S} means it never expires
	 * @param prefixLength how many leading characters may appear in audit records and
	 * logs so that support can correlate without being able to open the page
	 */
	public record ResultTokens(

			@DefaultValue("256") int entropyBits,

			@DefaultValue("P180D") Duration timeToLive,

			@DefaultValue("8") int prefixLength) {

		/** Minimum entropy accepted for a result token, in bits. */
		public static final int MINIMUM_ENTROPY_BITS = 128;

		public ResultTokens {
			if (entropyBits < MINIMUM_ENTROPY_BITS) {
				throw new IllegalArgumentException("app.result-tokens.entropy-bits must be at least "
						+ MINIMUM_ENTROPY_BITS + " bits, but was " + entropyBits);
			}
		}

		/**
		 * Whether tokens issued with these settings ever expire.
		 * @return true when a finite time to live is configured
		 */
		public boolean expires() {
			return !this.timeToLive.isZero() && !this.timeToLive.isNegative();
		}
	}

}
