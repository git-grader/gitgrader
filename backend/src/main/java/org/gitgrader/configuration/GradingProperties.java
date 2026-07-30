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

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.util.unit.DataSize;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration of the sandbox that executes untrusted student code.
 *
 * <p>
 * Every default here is chosen to be safe rather than permissive. In particular
 * {@code networkEnabled} defaults to {@code false}: a grading container has no route off
 * the host, which removes data exfiltration, dependency confusion and "call home for the
 * answer" in one step. Turning it on is a deliberate, documented decision.
 *
 * @param runner which {@code GradingRunner} implementation to use
 * @param workingDirectory host directory holding per-run scratch space
 * @param maxParallelJobs how many grading containers may run at once
 * @param defaultTimeout wall clock ceiling for one grading run
 * @param defaultMemoryLimit memory ceiling for one grading container
 * @param defaultCpuLimit CPU ceiling for one grading container, in cores
 * @param defaultPidLimit process ceiling, which is what actually stops a fork bomb
 * @param networkEnabled whether grading containers get any network at all
 * @param logSizeLimit how much runner output is captured before truncation
 * @param synchronousTimeout how long a push may block waiting for a fast result before
 * the run is handed off to the background and only a link is returned
 * @param retainWorkspaces keep run directories after completion; debugging aid, off by
 * default because student code is untrusted content
 * @param docker settings specific to the Docker runner
 * @param queue settings of the database backed job queue
 */
@ConfigurationProperties(prefix = "grading")
@Validated
public record GradingProperties(

		@DefaultValue("docker") @NotBlank String runner,

		@DefaultValue("/data/grading") @NotBlank String workingDirectory,

		@DefaultValue("2") @Min(1) int maxParallelJobs,

		@DefaultValue("120s") Duration defaultTimeout,

		@DefaultValue("512MB") DataSize defaultMemoryLimit,

		@DefaultValue("1.0") @Positive double defaultCpuLimit,

		@DefaultValue("256") @Min(16) int defaultPidLimit,

		@DefaultValue("false") boolean networkEnabled,

		@DefaultValue("1MB") DataSize logSizeLimit,

		@DefaultValue("20s") Duration synchronousTimeout,

		@DefaultValue("false") boolean retainWorkspaces,

		@DefaultValue Docker docker,

		@DefaultValue Queue queue) {

	/**
	 * Docker runner specifics.
	 *
	 * @param host Docker endpoint; a socket proxy can be pointed at here to avoid giving
	 * the application container full access to the engine
	 * @param workspaceMountRoot host path that the engine sees for run workspaces, which
	 * differs from {@code workingDirectory} when GitGrader itself runs in a container
	 * @param user uid:gid the sandbox process runs as; never root
	 * @param pullTimeout how long an image pull may take
	 * @param readOnlyRootFilesystem mount the container root read only
	 * @param tmpfsSize size of the writable {@code /tmp} handed to the sandbox
	 * @param dropAllCapabilities drop every Linux capability
	 * @param noNewPrivileges set the {@code no-new-privileges} security option
	 */
	public record Docker(

			@DefaultValue("unix:///var/run/docker.sock") @NotBlank String host,

			@DefaultValue("") String workspaceMountRoot,

			@DefaultValue("") String testsMountRoot,

			@DefaultValue("65534:65534") @NotBlank String user,

			@DefaultValue("5m") Duration pullTimeout,

			@DefaultValue("true") boolean readOnlyRootFilesystem,

			@DefaultValue("64MB") DataSize tmpfsSize,

			@DefaultValue("true") boolean dropAllCapabilities,

			@DefaultValue("true") boolean noNewPrivileges) {
	}

	/**
	 * Database backed job queue.
	 *
	 * <p>
	 * Deliberately not an external broker. A single PostgreSQL instance with
	 * {@code SELECT ... FOR UPDATE SKIP LOCKED} is enough for the load this platform
	 * sees, and it removes an entire service from the self-hosting story. The interface
	 * is narrow enough that a broker can be slid underneath later.
	 *
	 * @param pollInterval how often an idle worker looks for new work
	 * @param claimTimeout after how long a claimed job is considered abandoned and
	 * returned to the queue, which is what makes a crashed worker recoverable
	 * @param maxAttempts how often an infrastructure failure is retried before the run is
	 * marked {@code INFRASTRUCTURE_ERROR}
	 * @param retryBackoff base delay between attempts
	 */
	public record Queue(

			@DefaultValue("2s") Duration pollInterval,

			@DefaultValue("15m") Duration claimTimeout,

			@DefaultValue("3") @Min(1) int maxAttempts,

			@DefaultValue("30s") Duration retryBackoff) {
	}

}
