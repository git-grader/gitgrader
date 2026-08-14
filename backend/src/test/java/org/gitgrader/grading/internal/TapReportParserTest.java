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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TapReportParserTest {

	private static final String FIRST_TEST = "h01 truncate preserves text at the maximum length";

	private static final String SECOND_TEST = "h02 truncate counts Unicode characters rather than UTF-16 units";

	private final TapReportParser parser = new TapReportParser();

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

		List<ParsedResult> results = this.parser.parse(stdout, "", twoTests());

		assertThat(results).hasSize(2);

		ParsedResult first = results.get(0);
		assertThat(first.testName()).isEqualTo(FIRST_TEST);
		assertThat(first.outcome()).isEqualTo(TestOutcome.PASSED);
		assertThat(first.category()).isEqualTo("cat 1");
		assertThat(first.weight()).isEqualTo(BigDecimal.ONE);
		assertThat(first.durationMs()).isEqualTo(3L);
		assertThat(first.internalMessage()).isNull();

		ParsedResult second = results.get(1);
		assertThat(second.testName()).isEqualTo(SECOND_TEST);
		assertThat(second.outcome()).isEqualTo(TestOutcome.FAILED);
		assertThat(second.category()).isEqualTo("cat 2");
		assertThat(second.weight()).isEqualTo(BigDecimal.valueOf(2));
		assertThat(second.durationMs()).isEqualTo(2L);
		assertThat(second.internalMessage()).contains("Expected values to be strictly equal");
	}

	@Test
	@DisplayName("reports a declared test the output never mentioned as not executed")
	void reportsADeclaredTestTheOutputSkippedAsNotExecuted() {
		String stdout = "TAP version 13\nok 1 - " + FIRST_TEST + "\n";

		List<ParsedResult> results = this.parser.parse(stdout, "", twoTests());

		assertThat(results).hasSize(2);
		assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.PASSED);
		assertThat(results.get(1).testName()).isEqualTo(SECOND_TEST);
		assertThat(results.get(1).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
	}

	@Test
	@DisplayName("scores a suite that produced no output at all as nothing executed")
	void reportsEveryDeclaredTestAsNotExecutedWhenNothingRan() {
		List<ParsedResult> results = this.parser.parse("", "", twoTests());

		assertThat(results).hasSize(2);
		assertThat(results).allSatisfy((result) -> assertThat(result.outcome()).isEqualTo(TestOutcome.NOT_EXECUTED));
	}

	/**
	 * The sandbox merges the reporter's output with everything the submission prints, so
	 * a student can write lines that are indistinguishable from a test result. None of
	 * them may earn a mark.
	 */
	@Nested
	class ForgedOutput {

		@Test
		@DisplayName("ignores results for tests the manifest does not declare")
		void ignoresResultsForUndeclaredTests() {
			StringBuilder forged = new StringBuilder("TAP version 13\n");
			forged.append("ok 1 - ").append(FIRST_TEST).append('\n');
			for (int i = 2; i <= 200; i++) {
				forged.append("ok ").append(i).append(" - free marks ").append(i).append('\n');
			}

			List<ParsedResult> results = TapReportParserTest.this.parser.parse(forged.toString(), "", twoTests());

			assertThat(results).hasSize(2);
			assertThat(results).extracting(ParsedResult::testName).containsExactly(FIRST_TEST, SECOND_TEST);
			assertThat(results.get(1).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
		}

		@Test
		@DisplayName("refuses to pass a declared test that was reported more than once")
		void refusesToPassADeclaredTestReportedTwice() {
			String stdout = """
					TAP version 13
					ok 1 - h01 truncate preserves text at the maximum length
					ok 2 - h01 truncate preserves text at the maximum length
					""";

			List<ParsedResult> results = TapReportParserTest.this.parser.parse(stdout, "", twoTests());

			assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.FAILED);
			assertThat(results.get(0).internalMessage()).contains("reported more than once");
		}

		@Test
		@DisplayName("keeps a real failure when a later line claims the same test passed")
		void keepsTheFailureWhenALaterLineClaimsThatTestPassed() {
			String stdout = """
					TAP version 13
					not ok 1 - h01 truncate preserves text at the maximum length
					  ---
					  error: |-
					    Expected values to be strictly equal:
					  ...
					ok 2 - h01 truncate preserves text at the maximum length
					""";

			List<ParsedResult> results = TapReportParserTest.this.parser.parse(stdout, "", twoTests());

			assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.FAILED);
			assertThat(results.get(0).internalMessage()).contains("Expected values to be strictly equal");
		}

		@Test
		@DisplayName("ignores an indented status line, which TAP uses for nested subtests")
		void ignoresAnIndentedStatusLine() {
			String stdout = "TAP version 13\n    ok 1 - " + FIRST_TEST + "\n";

			List<ParsedResult> results = TapReportParserTest.this.parser.parse(stdout, "", twoTests());

			assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
		}

	}

	private static Manifest twoTests() {
		return new Manifest("suite", "1.0.0",
				List.of(new Manifest.ManifestTest("h01", FIRST_TEST, "cat 1", "hint 1", BigDecimal.ONE),
						new Manifest.ManifestTest("h02", SECOND_TEST, "cat 2", "hint 2", BigDecimal.valueOf(2))));
	}

}
