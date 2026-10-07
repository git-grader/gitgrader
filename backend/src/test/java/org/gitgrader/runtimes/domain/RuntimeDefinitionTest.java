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

import org.gitgrader.runtimes.RuntimeReportFormat;
import org.gitgrader.runtimes.NewRuntime;
import org.gitgrader.runtimes.ShimTopology;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.jspecify.annotations.Nullable;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link RuntimeDefinition}, the image and topology a grading run is pinned to.
 *
 * <p>
 * The sandbox reference is always built from the digest and never from the tag, because a
 * tag is mutable and a moving one would silently change what student code runs against.
 * The grading topology is mandatory for the same reason: the shim and legacy paths return
 * different marks for the same submission.
 */
class RuntimeDefinitionTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);

	private static final String DIGEST = "sha256:" + "a".repeat(64);

	@Test
	@DisplayName("always builds the sandbox reference from the digest rather than the tag")
	void sandboxReferenceAlwaysUsesTheDigestRatherThanTheTag() {
		RuntimeDefinition runtime = runtime("25", DIGEST);

		assertThat(runtime.pinnedReference()).isEqualTo("ghcr.io/git-grader/java@" + DIGEST);
		assertThat(runtime.toView().pinnedReference()).isEqualTo("ghcr.io/git-grader/java@" + DIGEST);
	}

	@Test
	@DisplayName("rejects a malformed digest")
	void malformedDigestIsRejected() {
		assertThatThrownBy(() -> runtime("25", "sha256:ABC")).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("digest");
	}

	@Test
	@DisplayName("rejects the moving latest tag regardless of case")
	void movingLatestTagIsRejectedRegardlessOfCase() {
		assertThatThrownBy(() -> runtime("LATEST", DIGEST)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("latest");
	}

	@Test
	@DisplayName("stores the declared single-sandbox topology the way legacy runtimes already are")
	void legacyTopologyIsStoredAsAbsent() {
		// The column keeps NULL for "one sandbox", so a runtime created now and one
		// created before the shim existed are the same row to every part of the grading
		// path.
		RuntimeDefinition runtime = runtime("25", DIGEST);

		assertThat(runtime.toView().shimKind()).isNull();
	}

	@Test
	@DisplayName("stores a declared shim kind so grading splits the run in two")
	void shimTopologyIsStoredAsDeclared() {
		RuntimeDefinition runtime = runtime("25", DIGEST, "node", null);

		assertThat(runtime.toView().shimKind()).isEqualTo("node");
	}

	@Test
	@DisplayName("refuses a runtime that does not say which topology it grades with")
	void refusesAnUndeclaredTopology() {
		// The defect this closes: a blank topology silently picked the grading path, and
		// the two paths return different marks for the same submission.
		assertThatThrownBy(() -> runtime("25", DIGEST, null, null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("grading topology");
		assertThatThrownBy(() -> runtime("25", DIGEST, "  ", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("grading topology");
	}

	@Test
	@DisplayName("refuses a topology name that is not a plain lowercase slug")
	void refusesAMalformedTopologyName() {
		assertThatThrownBy(() -> runtime("25", DIGEST, "Node Shim", null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("lowercase");
		assertThatThrownBy(() -> runtime("25", DIGEST, "node shim; rm -rf /", null))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("refuses a shim command on a runtime that starts no shim")
	void refusesAShimCommandOnALegacyRuntime() {
		assertThatThrownBy(() -> runtime("25", DIGEST, ShimTopology.LEGACY, "node /opt/gitgrader-shim/server.js"))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("shim command");
	}

	private static RuntimeDefinition runtime(String tag, String digest) {
		return runtime(tag, digest, ShimTopology.LEGACY, null);
	}

	private static RuntimeDefinition runtime(String tag, String digest, @Nullable String shimKind,
			@Nullable String shimCommand) {
		return new RuntimeDefinition(new NewRuntime("java-25", "Java 25", "ghcr.io/git-grader/java", tag, digest, null,
				"./mvnw test", RuntimeReportFormat.JUNIT_XML, true, shimKind, shimCommand), CLOCK);
	}

}
