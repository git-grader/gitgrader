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

package org.gitgrader.templates;

import java.util.UUID;

/**
 * A published test-suite version together with the name of the suite it belongs to.
 *
 * <p>
 * The counts are the declared ones recorded at publication, not a reading of the suite's
 * contents, so nothing here discloses the hidden tests themselves.
 *
 * @param id version identifier, the value an assignment stores
 * @param suiteName name of the owning test suite
 * @param versionLabel label the instructor gave this version
 * @param hiddenTestCount declared number of hidden tests
 * @param publicTestCount declared number of public tests
 */
public record PublishedTestSuiteVersionView(UUID id, String suiteName, String versionLabel, int hiddenTestCount,
		int publicTestCount) {
}
