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
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.gitgrader.grading.TestOutcome;
import org.springframework.stereotype.Component;

/**
 * Parses node test runner TAP output.
 */
@Component
public class TapReportParser implements ReportParser {

	private static final Pattern TEST_LINE_PATTERN = Pattern.compile("^(not ok|ok)\\s+\\d+\\s+-\\s+(.*)$");

	private static final Pattern DURATION_PATTERN = Pattern.compile("^\\s+duration_ms:\\s+([0-9.]+)$");

	@Override
	public List<ParsedResult> parse(String stdout, String stderr, Manifest manifest) {
		Map<String, Manifest.ManifestTest> manifestTests = manifest.tests()
			.stream()
			.collect(Collectors.toMap(Manifest.ManifestTest::name, Function.identity()));

		List<ParsedResult> results = new ArrayList<>();
		PendingTest pending = new PendingTest();
		boolean inYaml = false;

		for (String line : stdout.split("\\r?\\n")) {
			if (inYaml) {
				if ("...".equals(line.trim())) {
					inYaml = false;
				}
				else {
					pending.appendDiagnostic(line);
				}
				continue;
			}

			Matcher matcher = TEST_LINE_PATTERN.matcher(line);
			if (matcher.matches()) {
				// A new test line closes the previous one. Flushing here rather than only
				// on the YAML terminator is what stops a test that emitted no diagnostic
				// block from vanishing: a dropped test silently shrinks the denominator
				// and changes the student's score.
				pending.flushInto(results, manifestTests);
				pending.start(matcher.group(2).trim(), "ok".equals(matcher.group(1)));
			}
			else if ("---".equals(line.trim()) && pending.isOpen()) {
				inYaml = true;
			}
		}

		pending.flushInto(results, manifestTests);
		return List.copyOf(results);
	}

	/**
	 * The test currently being assembled from the TAP stream.
	 *
	 * <p>
	 * TAP describes a test across several lines - a status line, then an optional YAML
	 * diagnostic block - so the parser has to carry state between lines. Holding that
	 * state in one small object keeps the scanning loop readable and makes "a test is
	 * only emitted once, when it is complete" a property of a single method instead of
	 * something the loop has to get right in three places.
	 */
	@SuppressWarnings("PMD.AvoidStringBufferField") // bounded and per-run; never long
													// lived
	private static final class PendingTest {

		private final StringBuilder diagnostics = new StringBuilder();

		private String name;

		private TestOutcome outcome;

		private Long durationMillis;

		void start(String testName, boolean passed) {
			this.name = testName;
			this.outcome = passed ? TestOutcome.PASSED : TestOutcome.FAILED;
			this.durationMillis = null;
			this.diagnostics.setLength(0);
		}

		boolean isOpen() {
			return this.name != null;
		}

		void appendDiagnostic(String line) {
			this.diagnostics.append(line).append('\n');
			Matcher duration = DURATION_PATTERN.matcher(line);
			if (duration.matches()) {
				this.durationMillis = parseDuration(duration.group(1));
			}
		}

		void flushInto(List<ParsedResult> results, Map<String, Manifest.ManifestTest> manifestTests) {
			if (this.name == null || this.outcome == null) {
				return;
			}
			Manifest.ManifestTest declared = manifestTests.get(this.name);
			// Only a failing test carries its diagnostic text forward, and even then only
			// for the instructor view; the redactor decides what a student may see.
			String detail = (this.outcome == TestOutcome.PASSED) ? null : this.diagnostics.toString().trim();

			results.add(new ParsedResult("HIDDEN", (declared != null) ? declared.category() : null, this.name, null,
					this.outcome, weightOf(declared), this.durationMillis, null, detail,
					(declared != null) ? declared.hint() : null));
			this.name = null;
			this.outcome = null;
		}

		private static BigDecimal weightOf(Manifest.ManifestTest declared) {
			if (declared == null || declared.weight() == null) {
				return BigDecimal.ONE;
			}
			return declared.weight();
		}

		private static Long parseDuration(String raw) {
			try {
				return (long) Double.parseDouble(raw);
			}
			catch (NumberFormatException expected) {
				// A malformed duration is cosmetic; it must never fail a grading run.
				return null;
			}
		}

	}

}
