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

package org.gitgrader.grading.internal;

import java.time.Duration;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.gitgrader.configuration.GradingProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Supplies the {@link DockerClient} used by {@link DockerGradingRunner}.
 *
 * <p>
 * The condition here deliberately mirrors the runner's own condition. If the two ever
 * disagree, the application fails at startup with an unsatisfied dependency rather than
 * anything a test with a mocked client would notice, so they are kept identical on
 * purpose.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "grading.runner", havingValue = "docker", matchIfMissing = true)
class DockerClientConfiguration {

	/**
	 * Extra headroom added to the longest expected blocking call.
	 *
	 * <p>
	 * The transport must outlast the operation it is carrying. If it did not, a slow but
	 * perfectly legitimate pull or grading run would be cut off by the HTTP layer and
	 * recorded as an infrastructure error, which would show up to a student as a failed
	 * submission they had no way to influence.
	 */
	private static final Duration RESPONSE_TIMEOUT_HEADROOM = Duration.ofMinutes(2);

	@Bean
	@ConditionalOnMissingBean
	DockerClient dockerClient(GradingProperties properties) {
		GradingProperties.Docker docker = properties.docker();
		DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder()
			.withDockerHost(docker.host())
			.build();
		DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder().dockerHost(config.getDockerHost())
			.sslConfig(config.getSSLConfig())
			// Every concurrent job holds a connection for the lifetime of its container,
			// so a pool smaller than the job limit would serialise grading behind the
			// transport instead of running the parallelism that was configured.
			.maxConnections(Math.max(properties.maxParallelJobs(), 1) + 1)
			.connectionTimeout(Duration.ofSeconds(30))
			.responseTimeout(responseTimeout(properties))
			.build();
		return DockerClientImpl.getInstance(config, httpClient);
	}

	/**
	 * Chooses a response timeout that outlasts the slowest legitimate Docker call.
	 * @param properties the grading configuration
	 * @return the timeout to apply to the transport
	 */
	private static Duration responseTimeout(GradingProperties properties) {
		Duration longestCall = properties.docker().pullTimeout();
		if (properties.defaultTimeout().compareTo(longestCall) > 0) {
			longestCall = properties.defaultTimeout();
		}
		return longestCall.plus(RESPONSE_TIMEOUT_HEADROOM);
	}

}
