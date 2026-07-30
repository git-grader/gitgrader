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

package org.gitgrader.templates.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TemplateVersionTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);

	@Test
	void publishedVersionRejectsMutation() {
		TemplateVersion version = new TemplateVersion(UUID.randomUUID(), "v1", "java/v1", CLOCK);
		version.publish("abc", 1, 3, "instructor", CLOCK);

		assertThatThrownBy(() -> version.changeStoragePath("java/changed")).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("immutable");
		assertThatThrownBy(() -> version.publish("def", 2, 4, "instructor", CLOCK))
			.isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("immutable");
	}

}
