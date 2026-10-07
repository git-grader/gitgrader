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

package org.gitgrader.testsupport;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Tests for {@link DockerAvailableCondition}, the gate every Testcontainers-backed test
 * passes through before it runs.
 *
 * <p>
 * Its subject is therefore the build as much as the code: a probe that blocks turns a
 * machine without Docker into a hung build rather than a skipped test. The probe has to
 * return on a deadline, treat a failed probe as unavailable rather than as an error, and
 * abandon the thread it started - as a daemon, or the build still waits at shutdown.
 */
class DockerAvailableConditionTest {

	private static final Duration PATIENCE = Duration.ofSeconds(2);

	@Test
	@DisplayName("gives up on an engine that accepts the connection but never answers")
	void doesNotWaitForeverOnAWedgedEngine() {
		// A wedged Docker Desktop holds the socket open and sends nothing. Probing it
		// without a deadline is what made the build hang instead of skipping.
		CountDownLatch neverAnswers = new CountDownLatch(1);

		long startedAt = System.nanoTime();
		boolean available = DockerAvailableCondition.withinTimeout(() -> {
			neverAnswers.await();
			return true;
		}, PATIENCE);
		Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

		assertThat(available).isFalse();
		assertThat(elapsed).isLessThan(Duration.ofSeconds(30));
	}

	@Test
	@DisplayName("reports a missing engine as unavailable instead of failing the run")
	void treatsAFailedProbeAsUnavailable() {
		assertThat(DockerAvailableCondition.withinTimeout(() -> {
			throw new IllegalStateException("no engine here");
		}, PATIENCE)).isFalse();
	}

	@Test
	@DisplayName("passes through an engine that answers in time")
	void acceptsAWorkingEngine() {
		assertThat(DockerAvailableCondition.withinTimeout(() -> true, PATIENCE)).isTrue();
		assertThat(DockerAvailableCondition.withinTimeout(() -> false, PATIENCE)).isFalse();
	}

	@Test
	@DisplayName("abandons the probe instead of waiting for it, and leaves no thread behind")
	void doesNotLeaveTheProbeThreadBehind() {
		// The abandoned probe used to be waited on by ExecutorService.close(), which is
		// how
		// a bounded probe still hung the build. It must return while the probe runs on.
		assertThatCode(() -> DockerAvailableCondition.withinTimeout(() -> {
			Thread.sleep(Duration.ofMinutes(5).toMillis());
			return true;
		}, PATIENCE)).doesNotThrowAnyException();

		// The abandoned probe is a daemon, so a build with no Docker still exits.
		assertThat(Thread.getAllStackTraces()
			.keySet()
			.stream()
			.noneMatch((thread) -> "docker-availability-probe".equals(thread.getName()) && !thread.isDaemon()))
			.isTrue();
	}

}
