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

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gitgrader.grading.TestOutcome;
import org.gitgrader.runtimes.RuntimeReportFormat;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * Parses TAP test runner output, as emitted by node's {@code --test} runner ("ok 1 -
 * name") and by custom TAP reporters ("ok - name").
 *
 * <p>
 * The manifest, not the output, decides which tests exist. That is a grading integrity
 * boundary rather than a convenience: the sandbox merges the reporter's output with
 * everything the student's own code writes to standard output, so a submission containing
 * {@code process.stdout.write("ok 1 - anything\n")} emits lines indistinguishable from
 * the reporter's. Scoring whatever matched let a student mint passing tests and dilute
 * the suite until the percentage said what they wanted.
 *
 * <p>
 * So exactly one result is produced per declared test, in manifest order, and a line
 * naming anything else is discarded. A declared test the output never reported is
 * {@code NOT_EXECUTED} - a suite that dies halfway is scored on what it was supposed to
 * run, not on what survived. A declared test reported more than once is not counted as
 * passed at all: a suite reports each test once, so a second line is either a broken
 * manifest or forgery, and neither should earn marks.
 *
 * <p>
 * What remains is that a student who guesses a hidden test's exact name can still forge a
 * pass for a test that never ran. Hidden names are therefore secret by design - the
 * result page shows a category and a hint, never a name - and closing that last gap needs
 * the reporter and the student's code in separate containers.
 */
@Component
public class TapReportParser implements ReportParser {

	// Accepts both node's TAP ("ok 1 - name") and the leaner "ok - name" emitted
	// by some custom reporters (for example a Jasmine TAP reporter). A missing
	// number still yields a name to join against the manifest.
	private static final Pattern TEST_LINE_PATTERN = Pattern.compile("^(not ok|ok)\\s+(?:\\d+\\s+)?-\\s+(.*)$");

	private static final Pattern DURATION_PATTERN = Pattern.compile("^\\s+duration_ms:\\s+([0-9.]+)$");

	@Override
	public boolean supports(RuntimeReportFormat format) {
		return RuntimeReportFormat.TAP == format;
	}

	@Override
	public List<ParsedResult> parse(String stdout, String stderr, Manifest manifest) {
		Map<String, ManifestJoin.Observation> observed = ManifestJoin.observe(stdout, manifest, TapReportParser::scan);
		return ManifestJoin.results(manifest, observed);
	}

	/**
	 * Walks the TAP stream, handing each completed test to the collector.
	 * @param stdout everything the sandbox wrote to standard output
	 * @param collector receives one call per reported test
	 */
	private static Void scan(String stdout, ManifestJoin.ReportedTest collector) {
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
				// block from vanishing: a dropped test would be reported as never having
				// run.
				pending.flushInto(collector);
				pending.start(matcher.group(2).trim(), "ok".equals(matcher.group(1)));
			}
			else if ("---".equals(line.trim()) && pending.isOpen()) {
				inYaml = true;
			}
		}
		pending.flushInto(collector);
		return null;
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

		private @Nullable String name;

		private @Nullable TestOutcome outcome;

		private @Nullable Long durationMillis;

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

		void flushInto(ManifestJoin.ReportedTest collector) {
			String reportedName = this.name;
			TestOutcome reportedOutcome = this.outcome;
			if (reportedName == null || reportedOutcome == null) {
				return;
			}
			collector.accept(reportedName, reportedOutcome, this.durationMillis, this.diagnostics.toString().trim());
			this.name = null;
			this.outcome = null;
		}

		private static @Nullable Long parseDuration(String raw) {
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
