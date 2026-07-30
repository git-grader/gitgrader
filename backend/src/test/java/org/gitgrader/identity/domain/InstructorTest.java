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

package org.gitgrader.identity.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class InstructorTest {

	@Test
	void loginRefreshesProjectionAndLastLoginTime() {
		Clock firstClock = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);
		Clock secondClock = Clock.fixed(Instant.parse("2026-03-02T10:15:30Z"), ZoneOffset.UTC);
		Instructor instructor = new Instructor("Ada", "Ada L", "old@example.test", Set.of("INSTRUCTOR"), firstClock);

		instructor.updateOnLogin("Ada Lovelace", "new@example.test", Set.of("ADMIN", "INSTRUCTOR"), secondClock);

		assertThat(instructor.toView().firstLoginAt()).isEqualTo(Instant.now(firstClock));
		assertThat(instructor.toView().lastLoginAt()).isEqualTo(Instant.now(secondClock));
		assertThat(instructor.toView().displayName()).isEqualTo("Ada Lovelace");
		assertThat(instructor.toView().roles()).isEqualTo("ADMIN,INSTRUCTOR");
	}

}
