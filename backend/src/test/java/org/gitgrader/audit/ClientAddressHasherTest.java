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

package org.gitgrader.audit;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link ClientAddressHasher}, which has to make a client address unusable as
 * an identifier while still grouping repeat callers.
 *
 * <p>
 * Both halves matter: a hash that varies between calls cannot support the per-address
 * limits the audit trail is read through, and the value is written into log lines and
 * paths, so it must be short, unpadded and URL-safe, and must not carry the address it
 * stands for.
 */
class ClientAddressHasherTest {

	@Test
	@DisplayName("creates a stable, URL-safe, truncated hash under the configured key")
	void createsStableUrlSafeTruncatedHashWithConfiguredKey() {
		ClientAddressHasher hasher = new ClientAddressHasher(new AuditProperties("server-key", Duration.ZERO));

		String first = hasher.hash("192.0.2.10");
		String second = hasher.hash("192.0.2.10");

		assertThat(first).isEqualTo(second).hasSize(32).matches("[A-Za-z0-9_-]{32}");
		assertThat(first).doesNotContain("192.0.2.10");
	}

}
