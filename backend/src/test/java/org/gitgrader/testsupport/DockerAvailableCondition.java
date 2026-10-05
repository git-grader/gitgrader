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
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.junit.jupiter.api.extension.ConditionEvaluationResult;
import org.junit.jupiter.api.extension.ExecutionCondition;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.DockerClientFactory;

/**
 * Decides whether a Docker-backed test can run here.
 *
 * @see EnabledIfDockerAvailable
 */
class DockerAvailableCondition implements ExecutionCondition {

	/**
	 * How long the availability probe may take before it is called a failure.
	 *
	 * <p>
	 * A Docker engine that is starting up, or one whose Desktop has wedged, accepts the
	 * connection on its socket and then never answers. Probing that naively blocks
	 * forever, which turns "no engine" into "the build never finishes" for every
	 * contributor who hits it, and on CI into a job that burns its whole timeout instead
	 * of failing on the missing engine it is supposed to report.
	 */
	private static final Duration PROBE_TIMEOUT = Duration.ofSeconds(20);

	private static Boolean availability;

	@Override
	public ConditionEvaluationResult evaluateExecutionCondition(ExtensionContext context) {
		if (dockerAvailable()) {
			return ConditionEvaluationResult.enabled("Docker is available");
		}
		if (runningOnCi()) {
			// Enabled on purpose, so the run fails on the missing engine rather than
			// reporting a green build that proved nothing.
			return ConditionEvaluationResult.enabled("No Docker engine, but CI must not skip integration tests");
		}
		return ConditionEvaluationResult.disabled("No Docker engine is reachable",
				"Start Docker to run the integration tests, or use -DskipITs to leave them out deliberately.");
	}

	/**
	 * Reports whether a Docker engine answers, at most once per JVM.
	 *
	 * <p>
	 * The answer is cached because this runs per test class, and because a wedged engine
	 * costs the full timeout every single time.
	 * @return true only when the engine answered in time and reported itself usable
	 */
	private static synchronized boolean dockerAvailable() {
		if (availability == null) {
			availability = withinTimeout(() -> DockerClientFactory.instance().isDockerAvailable());
		}
		return availability;
	}

	/**
	 * Runs a probe that may never answer, and gives up on it.
	 * @param probe the check to run
	 * @return the probe's answer, or false when it failed, threw, or ran too long
	 */
	static boolean withinTimeout(Callable<Boolean> probe) {
		return withinTimeout(probe, PROBE_TIMEOUT);
	}

	/**
	 * Runs a probe that may never answer, and gives up on it after a stated deadline.
	 * @param probe the check to run
	 * @param timeout how long to wait for an answer
	 * @return the probe's answer, or false when it failed, threw, or ran too long
	 */
	static boolean withinTimeout(Callable<Boolean> probe, Duration timeout) {
		ExecutorService executor = Executors.newSingleThreadExecutor((runnable) -> {
			Thread thread = new Thread(runnable, "docker-availability-probe");
			// Daemon, so a wedged engine cannot hold the JVM open after the tests are
			// done.
			thread.setDaemon(true);
			return thread;
		});
		// Deliberately not closed in a try-with-resources: ExecutorService.close() waits
		// for its tasks, and waiting for the task is precisely what this deadline exists
		// to stop. shutdownNow() interrupts the probe and returns.
		Future<Boolean> answer = null;
		try {
			answer = executor.submit(probe);
			return answer.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
		}
		catch (TimeoutException ex) {
			// The engine is there but not answering. Treated as unavailable: on CI this
			// fails the run, and locally it skips with an explanation.
			return false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			return false;
		}
		catch (ExecutionException | RuntimeException ex) {
			return false;
		}
		finally {
			if (answer != null) {
				answer.cancel(true);
			}
			executor.shutdownNow();
		}
	}

	private static boolean runningOnCi() {
		return System.getenv("CI") != null;
	}

}
