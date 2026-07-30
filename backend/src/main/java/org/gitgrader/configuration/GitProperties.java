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

import java.util.List;
import java.util.Set;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import org.springframework.util.unit.DataSize;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the embedded Git SSH endpoint.
 *
 * <p>
 * {@code sshHost} and {@code sshPort} are what students see in a clone command, which is
 * not necessarily what the process binds to: behind a reverse proxy or a container port
 * mapping the advertised address differs from the listen address, so the two are
 * configured independently.
 *
 * @param enabled whether the SSH endpoint starts at all; disabled in tests that only
 * exercise the web layer
 * @param sshHost host advertised in generated clone commands
 * @param sshPort port advertised in generated clone commands
 * @param listenAddress interface the SSH server binds to
 * @param listenPort port the SSH server binds to
 * @param sshUser the single fixed SSH user name; identity comes from the key, not this
 * name, exactly as on the large forges
 * @param hostKeyPath location of the persistent server host key
 * @param repositoryDirectory root directory holding all bare student repositories
 * @param maxPushSize largest accepted push
 * @param maxFileSize largest accepted single file inside a push
 * @param maxFileCount largest accepted number of files in the working tree
 * @param allowedKeyTypes SSH key blob types accepted for registration; note that these
 * are {@code authorized_keys} blob names such as {@code ssh-rsa}, not signature algorithm
 * names such as {@code rsa-sha2-256}, which never appear in a public key file
 * @param requireSignedCommits whether unsigned commits are rejected at push time
 * @param idleTimeout how long an idle SSH session is held open
 */
@ConfigurationProperties(prefix = "git")
@Validated
@SuppressWarnings("PMD.AvoidUsingHardCodedIP") // 0.0.0.0 is the bind-all default, not an
												// endpoint
public record GitProperties(

		@DefaultValue("true") boolean enabled,

		@DefaultValue("localhost") @NotBlank String sshHost,

		@DefaultValue("2222") @Min(1) @Max(65535) int sshPort,

		@DefaultValue("0.0.0.0") @NotBlank String listenAddress,

		@DefaultValue("2222") @Min(1) @Max(65535) int listenPort,

		@DefaultValue("git") @NotBlank String sshUser,

		@DefaultValue("/data/git/ssh/hostkey.ser") @NotBlank String hostKeyPath,

		@DefaultValue("/data/git/repositories") @NotBlank String repositoryDirectory,

		@DefaultValue("50MB") DataSize maxPushSize,

		@DefaultValue("10MB") DataSize maxFileSize,

		@DefaultValue("2000") @Min(1) int maxFileCount,

		@DefaultValue( {
				"ssh-ed25519", "sk-ssh-ed25519@openssh.com", "ecdsa-sha2-nistp256", "ecdsa-sha2-nistp384",
				"ecdsa-sha2-nistp521", "ssh-rsa" }) @NotEmpty Set<String> allowedKeyTypes,

		@DefaultValue("true") boolean requireSignedCommits,

		@DefaultValue("10m") java.time.Duration idleTimeout){

	/**
	 * Minimum accepted RSA modulus size.
	 *
	 * <p>
	 * Ed25519 is preferred and recommended in the UI; RSA is still accepted because
	 * institutional key material often predates Ed25519, but only at a size that is still
	 * defensible.
	 */
	public static final int MINIMUM_RSA_KEY_BITS = 3072;

	/** Branch names a student is permitted to update. */
	public static final List<String> ALLOWED_REF_PREFIXES = List.of("refs/heads/");

	/**
	 * Builds the clone URL a student is shown for one assignment repository.
	 * @param repositoryPath repository path relative to the repository root, for example
	 * {@code course-a/assignment-01/12345}
	 * @return an {@code ssh://} clone URL built purely from configuration
	 */
	public String cloneUrl(String repositoryPath) {
		StringBuilder url = new StringBuilder(64);
		url.append("ssh://").append(this.sshUser).append('@').append(this.sshHost);
		if (this.sshPort != 22) {
			url.append(':').append(this.sshPort);
		}
		url.append('/').append(repositoryPath);
		if (!repositoryPath.endsWith(".git")) {
			url.append(".git");
		}
		return url.toString();
	}

}
