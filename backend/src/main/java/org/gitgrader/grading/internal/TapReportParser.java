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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gitgrader.grading.TestOutcome;
import org.gitgrader.grading.domain.TestResultRecord;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Parses node test runner TAP output.
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

	private static final Pattern TEST_LINE_PATTERN = Pattern.compile("^(not ok|ok)\\s+\\d+\\s+-\\s+(.*)$");

	private static final Pattern DURATION_PATTERN = Pattern.compile("^\\s+duration_ms:\\s+([0-9.]+)$");

	/** How many undeclared names are named in the warning that reports them. */
	private static final int MAX_REPORTED_UNDECLARED = 5;

	/** Ceiling on one undeclared name in that warning; the output is untrusted. */
	private static final int MAX_REPORTED_NAME_LENGTH = 80;

	private static final Logger logger = LoggerFactory.getLogger(TapReportParser.class);

	@Override
	public List<ParsedResult> parse(String stdout, String stderr, Manifest manifest) {
		Map<String, Manifest.ManifestTest> declared = declaredTests(manifest);
		Map<String, Observation> observed = HashMap.newHashMap(declared.size());
		List<String> undeclared = new ArrayList<>();

		scan(stdout, (name, outcome, durationMillis, diagnostics) -> {
			if (!declared.containsKey(name)) {
				undeclared.add(name);
				return;
			}
			observed.merge(name, new Observation(outcome, durationMillis, diagnostics), Observation::mergedWith);
		});

		reportUndeclared(undeclared);
		return declared.values().stream().map((test) -> toResult(test, observed.get(test.name()))).toList();
	}

	/**
	 * Indexes the declared tests by name, preserving manifest order.
	 *
	 * <p>
	 * A manifest that names the same test twice keeps the first entry rather than
	 * failing: the duplicate carries no information the first does not, and refusing to
	 * grade over it would punish a class for an operator's copy-paste.
	 * @param manifest the operator's declaration of the suite
	 * @return declared tests, keyed by the name the reporter emits
	 */
	private static Map<String, Manifest.ManifestTest> declaredTests(Manifest manifest) {
		Map<String, Manifest.ManifestTest> declared = LinkedHashMap.newLinkedHashMap(manifest.tests().size());
		for (Manifest.ManifestTest test : manifest.tests()) {
			declared.putIfAbsent(test.name(), test);
		}
		return declared;
	}

	/**
	 * Walks the TAP stream, handing each completed test to the collector.
	 * @param stdout everything the sandbox wrote to standard output
	 * @param collector receives one call per reported test
	 */
	private static void scan(String stdout, ReportedTest collector) {
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
	}

	/**
	 * Turns one declared test and what the output said about it into a result.
	 * @param test the declared test
	 * @param observation what the output reported, or {@code null} if it never did
	 * @return the result carried forward to scoring and storage
	 */
	private static ParsedResult toResult(Manifest.ManifestTest test, @Nullable Observation observation) {
		TestOutcome outcome = (observation != null) ? observation.outcome() : TestOutcome.NOT_EXECUTED;
		// Only a test that did not pass carries its diagnostic text forward, and even
		// then only for the instructor view; the redactor decides what a student may see.
		String detail = (observation != null && outcome != TestOutcome.PASSED) ? observation.diagnostics() : null;
		Long durationMillis = (observation != null) ? observation.durationMillis() : null;
		BigDecimal weight = (test.weight() != null) ? test.weight() : BigDecimal.ONE;

		return new ParsedResult(TestResultRecord.VISIBILITY_HIDDEN, test.category(), test.name(), null, outcome, weight,
				durationMillis, null, detail, test.hint());
	}

	/**
	 * Warns when the output named tests the manifest does not declare.
	 *
	 * <p>
	 * Almost always a manifest that drifted from the suite, and without the offending
	 * names an operator cannot tell which. They come from untrusted output, so they are
	 * stripped of control characters and truncated before reaching the log.
	 * @param undeclared every undeclared name the output reported
	 */
	private static void reportUndeclared(List<String> undeclared) {
		if (undeclared.isEmpty()) {
			return;
		}
		List<String> sample = undeclared.stream()
			.distinct()
			.limit(MAX_REPORTED_UNDECLARED)
			.map(TapReportParser::sanitise)
			.toList();
		logger.warn("Ignored {} test result line(s) naming tests the manifest does not declare; "
				+ "either the manifest has drifted from the suite or the submission wrote them itself. "
				+ "First names seen: {}", undeclared.size(), sample);
	}

	private static String sanitise(String name) {
		String stripped = name.replaceAll("\\p{Cntrl}", "");
		return (stripped.length() > MAX_REPORTED_NAME_LENGTH) ? stripped.substring(0, MAX_REPORTED_NAME_LENGTH) + "…"
				: stripped;
	}

	/**
	 * Receives each test the TAP stream reported.
	 */
	@FunctionalInterface
	private interface ReportedTest {

		/**
		 * Accepts one reported test.
		 * @param name the name on the status line
		 * @param outcome whether it passed
		 * @param durationMillis how long it took, when the stream said
		 * @param diagnostics the YAML block that followed, trimmed
		 */
		void accept(String name, TestOutcome outcome, @Nullable Long durationMillis, String diagnostics);

	}

	/**
	 * What the output said about one declared test.
	 *
	 * @param outcome how it ended
	 * @param durationMillis how long it took, when the stream said
	 * @param diagnostics the YAML block that followed
	 */
	private record Observation(TestOutcome outcome, @Nullable Long durationMillis, String diagnostics) {

		/**
		 * Combines a repeated report of the same test.
		 *
		 * <p>
		 * A suite reports each test once, so a second line is either a manifest naming
		 * two tests the same or output that was written rather than run. The merged
		 * result never passes, and keeps whichever report explains why.
		 * @param other the later report
		 * @return the observation that stands
		 */
		Observation mergedWith(Observation other) {
			if (this.outcome != TestOutcome.PASSED) {
				return this;
			}
			if (other.outcome != TestOutcome.PASSED) {
				return other;
			}
			return new Observation(TestOutcome.FAILED, this.durationMillis,
					"This test was reported more than once, so its result could not be trusted.");
		}
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

		void flushInto(ReportedTest collector) {
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
