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

import java.math.BigDecimal;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.jspecify.annotations.Nullable;

/**
 * Representation of the manifest.json file.
 */
record Manifest(@JsonProperty("suiteKey") String suiteKey, @JsonProperty("version") String version,
		@JsonProperty("tests") List<ManifestTest> tests) {

	record ManifestTest(@JsonProperty("id") String id, @JsonProperty("name") String name,
			@JsonProperty("category") @Nullable String category, @JsonProperty("hint") @Nullable String hint,
			@JsonProperty("weight") BigDecimal weight) {
	}
}
