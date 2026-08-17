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

package org.gitgrader.runtimes.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.gitgrader.runtimes.ReportFormat;
import org.gitgrader.runtimes.NewRuntime;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuntimeDefinitionTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);

	private static final String DIGEST = "sha256:" + "a".repeat(64);

	@Test
	void sandboxReferenceAlwaysUsesTheDigestRatherThanTheTag() {
		RuntimeDefinition runtime = runtime("25", DIGEST);

		assertThat(runtime.pinnedReference()).isEqualTo("ghcr.io/git-grader/java@" + DIGEST);
		assertThat(runtime.toView().pinnedReference()).isEqualTo("ghcr.io/git-grader/java@" + DIGEST);
	}

	@Test
	void malformedDigestIsRejected() {
		assertThatThrownBy(() -> runtime("25", "sha256:ABC")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("digest");
	}

	@Test
	void movingLatestTagIsRejectedRegardlessOfCase() {
		assertThatThrownBy(() -> runtime("LATEST", DIGEST)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("latest");
	}

	private static RuntimeDefinition runtime(String tag, String digest) {
		return new RuntimeDefinition(new NewRuntime("java-25", "Java 25", "ghcr.io/git-grader/java", tag, digest, null,
				"./mvnw test", ReportFormat.JUNIT_XML, true), CLOCK);
	}

}
