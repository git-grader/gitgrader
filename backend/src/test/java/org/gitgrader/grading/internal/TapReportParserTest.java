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

import org.gitgrader.grading.TestOutcome;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TapReportParserTest {

	@Test
	void parsesTapOutputSuccessfully() {
		String stdout = """
				TAP version 13
				# Subtest: h01 truncate preserves text at the maximum length
				ok 1 - h01 truncate preserves text at the maximum length
				  ---
				  duration_ms: 3.888521
				  type: 'test'
				  ...
				# Subtest: h02 truncate counts Unicode characters rather than UTF-16 units
				not ok 2 - h02 truncate counts Unicode characters rather than UTF-16 units
				  ---
				  duration_ms: 2.201477
				  type: 'test'
				  error: |-
				    Expected values to be strictly equal:
				  ...
				""";

		Manifest manifest = new Manifest("suite", "1.0.0", List.of(
				new Manifest.ManifestTest("h01", "h01 truncate preserves text at the maximum length", "cat 1", "hint 1",
						BigDecimal.ONE),
				new Manifest.ManifestTest("h02", "h02 truncate counts Unicode characters rather than UTF-16 units",
						"cat 2", "hint 2", BigDecimal.valueOf(2))));

		TapReportParser parser = new TapReportParser();
		List<ParsedResult> results = parser.parse(stdout, "", manifest);

		assertThat(results).hasSize(2);

		ParsedResult r1 = results.get(0);
		assertThat(r1.testName()).isEqualTo("h01 truncate preserves text at the maximum length");
		assertThat(r1.outcome()).isEqualTo(TestOutcome.PASSED);
		assertThat(r1.category()).isEqualTo("cat 1");
		assertThat(r1.weight()).isEqualTo(BigDecimal.ONE);
		assertThat(r1.durationMs()).isEqualTo(3L);
		assertThat(r1.internalMessage()).isNull();

		ParsedResult r2 = results.get(1);
		assertThat(r2.testName()).isEqualTo("h02 truncate counts Unicode characters rather than UTF-16 units");
		assertThat(r2.outcome()).isEqualTo(TestOutcome.FAILED);
		assertThat(r2.category()).isEqualTo("cat 2");
		assertThat(r2.weight()).isEqualTo(BigDecimal.valueOf(2));
		assertThat(r2.durationMs()).isEqualTo(2L);
		assertThat(r2.internalMessage()).contains("Expected values to be strictly equal");
	}

}
