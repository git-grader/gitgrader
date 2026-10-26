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

/**
 * Tests for the JUnit XML reading in {@link JUnitXmlReportParser}.
 *
 * <p>
 * The reporter's output is not trusted to decide who ran. The manifest is the authority
 * on which tests exist and what each is worth, so a declared test the report never
 * mentioned is reported as not executed rather than dropped, and a suite that produced
 * nothing at all scores as nothing run instead of an empty pass. Diagnostics, durations
 * and per-test weights are carried through from the parsed elements, and the outcome
 * counts keep reporting tests rather than weights so the interface can say how many
 * checks ran.
 */
class JUnitXmlReportParserTest {

	private static final String FIRST_TEST = "h01 truncate preserves text at the maximum length";

	private static final String SECOND_TEST = "h02 truncate counts Unicode characters rather than UTF-16 units";

	private final JUnitXmlReportParser parser = new JUnitXmlReportParser();

	@Test
	@DisplayName("parses a JUnit XML report, including failure text, into per-test results")
	void parsesJUnitXmlSuccessfully() {
		String stdout = """
				<?xml version="1.0" encoding="UTF-8"?>
				<testsuite name="pytest" tests="2" failures="1" errors="0" skipped="0">
				  <testcase classname="test_string_utils" name="h01 truncate preserves text at the maximum length" time="0.003"/>
				  <testcase classname="test_string_utils" name="h02 truncate counts Unicode characters rather than UTF-16 units" time="0.002">
				    <failure message="AssertionError">assert 'ex' == 'exact'</failure>
				  </testcase>
				</testsuite>
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
		assertThat(second.internalMessage()).contains("assert 'ex' == 'exact'");
	}

	@Test
	@DisplayName("treats an error element the same way as a failure")
	void treatsAnErrorElementAsAFailure() {
		String stdout = """
				<testsuite name="pytest" tests="1">
				  <testcase name="h01 truncate preserves text at the maximum length">
				    <error type="ZeroDivisionError">division by zero</error>
				  </testcase>
				</testsuite>
				""";

		List<ParsedResult> results = this.parser.parse(stdout, "", twoTests());

		assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.FAILED);
		assertThat(results.get(0).internalMessage()).contains("division by zero");
	}

	@Test
	@DisplayName("reports a declared test the report never mentioned as not executed")
	void reportsADeclaredTestTheReportSkippedAsNotExecuted() {
		String stdout = """
				<testsuite name="pytest" tests="1">
				  <testcase name="h01 truncate preserves text at the maximum length" time="0.001"/>
				</testsuite>
				""";

		List<ParsedResult> results = this.parser.parse(stdout, "", twoTests());

		assertThat(results).hasSize(2);
		assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.PASSED);
		assertThat(results.get(1).testName()).isEqualTo(SECOND_TEST);
		assertThat(results.get(1).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
	}

	@Test
	@DisplayName("scores a report that named nothing at all as nothing executed")
	void reportsEveryDeclaredTestAsNotExecutedWhenNothingRan() {
		List<ParsedResult> results = this.parser.parse("", "", twoTests());

		assertThat(results).hasSize(2);
		assertThat(results).allSatisfy((result) -> assertThat(result.outcome()).isEqualTo(TestOutcome.NOT_EXECUTED));
	}

	@Test
	@DisplayName("reads a skipped testcase as not executed rather than failed")
	void readsASkippedTestcaseAsNotExecuted() {
		String stdout = """
				<testsuite name="pytest" tests="2">
				  <testcase name="h01 truncate preserves text at the maximum length" time="0.001">
				    <skipped message="conditional skip">not on this platform</skipped>
				  </testcase>
				  <testcase name="h02 truncate counts Unicode characters rather than UTF-16 units" time="0.002"/>
				</testsuite>
				""";

		List<ParsedResult> results = this.parser.parse(stdout, "", twoTests());

		assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
		assertThat(results.get(1).outcome()).isEqualTo(TestOutcome.PASSED);
	}

	@Test
	@DisplayName("rounds the report's fractional seconds into whole milliseconds")
	void convertsSecondsIntoMilliseconds() {
		String stdout = """
				<testsuite name="pytest" tests="1">
				  <testcase name="h01 truncate preserves text at the maximum length" time="1.2345"/>
				</testsuite>
				""";

		List<ParsedResult> results = this.parser.parse(stdout, "", twoTests());

		assertThat(results.get(0).durationMs()).isEqualTo(1235L);
	}

	/**
	 * The sandbox merges the reporter's output with everything the submission prints, so
	 * a student can write XML fragments that are indistinguishable from a test report.
	 * None of them may earn a mark.
	 */
	@Nested
	@DisplayName("forged output")
	class ForgedOutput {

		@Test
		@DisplayName("ignores results for tests the manifest does not declare")
		void ignoresResultsForUndeclaredTests() {
			StringBuilder forged = new StringBuilder("<testsuite name=\"x\" tests=\"200\">\n");
			forged.append("<testcase name=\"").append(FIRST_TEST).append("\" time=\"0.001\"/>\n");
			for (int i = 2; i <= 200; i++) {
				forged.append("<testcase name=\"free marks ").append(i).append("\" time=\"0.001\"/>\n");
			}
			forged.append("</testsuite>\n");

			List<ParsedResult> results = JUnitXmlReportParserTest.this.parser.parse(forged.toString(), "", twoTests());

			assertThat(results).hasSize(2);
			assertThat(results).extracting(ParsedResult::testName).containsExactly(FIRST_TEST, SECOND_TEST);
			assertThat(results.get(1).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
		}

		@Test
		@DisplayName("refuses to pass a declared test that was reported more than once")
		void refusesToPassADeclaredTestReportedTwice() {
			String stdout = """
					<testsuite name="pytest" tests="2">
					  <testcase name="h01 truncate preserves text at the maximum length" time="0.001"/>
					  <testcase name="h01 truncate preserves text at the maximum length" time="0.001"/>
					</testsuite>
					""";

			List<ParsedResult> results = JUnitXmlReportParserTest.this.parser.parse(stdout, "", twoTests());

			assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.FAILED);
			assertThat(results.get(0).internalMessage()).contains("reported more than once");
		}

		@Test
		@DisplayName("keeps a real failure when a later element claims the same test passed")
		void keepsTheFailureWhenALaterElementClaimsThatTestPassed() {
			String stdout = """
					<testsuite name="pytest" tests="1">
					  <testcase name="h01 truncate preserves text at the maximum length" time="0.001">
					    <failure message="AssertionError">assert 'ex' == 'exact'</failure>
					  </testcase>
					  <testcase name="h01 truncate preserves text at the maximum length" time="0.002"/>
					</testsuite>
					""";

			List<ParsedResult> results = JUnitXmlReportParserTest.this.parser.parse(stdout, "", twoTests());

			assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.FAILED);
			assertThat(results.get(0).internalMessage()).contains("assert 'ex' == 'exact'");
		}

		@Test
		@DisplayName("keeps the real outcome when student output precedes the report")
		void keepsTheOutcomeWhenStudentOutputPrecedesTheReport() {
			String stdout = """
					Traceback (most recent call last):
					  File "solution.py", line 1, in <module>
					<testsuite name="pytest" tests="1">
					  <testcase name="h01 truncate preserves text at the maximum length" time="0.001"/>
					</testsuite>
					""";

			List<ParsedResult> results = JUnitXmlReportParserTest.this.parser.parse(stdout, "", twoTests());

			assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
		}

		@Test
		@DisplayName("keeps the real outcome when a DOCTYPE declares an external entity")
		void ignoresADoctypeDeclaringExternalEntities() {
			String stdout = """
					<!DOCTYPE testsuite SYSTEM "http://example.invalid/evil.dtd">
					<testsuite name="pytest" tests="1">
					  <testcase name="h01 truncate preserves text at the maximum length" time="0.001"/>
					</testsuite>
					""";

			List<ParsedResult> results = JUnitXmlReportParserTest.this.parser.parse(stdout, "", twoTests());

			// A DOCTYPE can only come from something other than the reporter, so the
			// stream is untrusted from the start and nothing in it may be scored.
			assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
		}

	}

	@Test
	@DisplayName("keeps the testcases parsed before a truncated report")
	void keepsTestcasesParsedBeforeATruncatedReport() {
		String stdout = """
				<testsuite name="pytest" tests="2">
				  <testcase name="h01 truncate preserves text at the maximum length" time="0.001"/>
				  <testcase name="h02 truncate counts Unicode characters rather than UTF-16
				""";

		List<ParsedResult> results = this.parser.parse(stdout, "", twoTests());

		assertThat(results.get(0).outcome()).isEqualTo(TestOutcome.PASSED);
		assertThat(results.get(1).outcome()).isEqualTo(TestOutcome.NOT_EXECUTED);
	}

	private static Manifest twoTests() {
		return new Manifest("suite", "1.0.0",
				List.of(new Manifest.ManifestTest("h01", FIRST_TEST, "cat 1", "hint 1", BigDecimal.ONE),
						new Manifest.ManifestTest("h02", SECOND_TEST, "cat 2", "hint 2", BigDecimal.valueOf(2))));
	}

}
