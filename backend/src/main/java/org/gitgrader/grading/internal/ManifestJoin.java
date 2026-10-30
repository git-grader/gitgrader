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
import java.util.function.BiFunction;

import org.gitgrader.grading.TestOutcome;
import org.gitgrader.grading.domain.TestResultRecord;
import org.jspecify.annotations.Nullable;

/**
 * The manifest join every report parser shares.
 *
 * <p>
 * The manifest, not the output, decides which tests exist. That is a grading integrity
 * boundary rather than a convenience: the sandbox merges the reporter's output with
 * everything the student's own code writes to standard output, so a submission can print
 * convincing test lines of its own. Scoring whatever parsed let a student mint passing
 * tests and dilute the suite until the percentage said what they wanted.
 *
 * <p>
 * So exactly one result is produced per declared test, in manifest order, and output
 * naming anything else is discarded. A declared test the report never named is
 * {@code NOT_EXECUTED} - a suite that dies halfway is scored on what it was supposed to
 * run, not on what survived. A declared test reported more than once is not counted as
 * passed at all: a suite reports each test once, so a second report is either a broken
 * manifest or forgery, and neither should earn marks.
 *
 * <p>
 * Each parser keeps only its own grammar; the collection of reports, the join against the
 * manifest and the shaping of results live here once.
 */
final class ManifestJoin {

	/**
	 * How many undeclared names are named in the warning that reports them.
	 */
	private static final int MAX_REPORTED_UNDECLARED = 5;

	/**
	 * Ceiling on one undeclared name in that warning; the output is untrusted.
	 */
	private static final int MAX_REPORTED_NAME_LENGTH = 80;

	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(ManifestJoin.class);

	private ManifestJoin() {
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
	static Map<String, Manifest.ManifestTest> declaredTests(Manifest manifest) {
		Map<String, Manifest.ManifestTest> declared = LinkedHashMap.newLinkedHashMap(manifest.tests().size());
		for (Manifest.ManifestTest test : manifest.tests()) {
			declared.putIfAbsent(test.name(), test);
		}
		return declared;
	}

	/**
	 * Collects the reports of one run and joins them against the manifest.
	 *
	 * <p>
	 * A name the manifest does not declare is ignored and remembered for the operator
	 * warning; a declared name reported more than once merges into an observation that
	 * never passes.
	 * @param stdout everything the runner wrote to standard output
	 * @param manifest the operator's declaration of the suite
	 * @param scan the parser's own reader, handing each completed test to a
	 * {@link ReportedTest}
	 * @return what the report said, keyed by declared test name
	 */
	static Map<String, Observation> observe(String stdout, Manifest manifest,
			BiFunction<String, ReportedTest, Void> scan) {
		Map<String, Manifest.ManifestTest> declared = declaredTests(manifest);
		Map<String, Observation> observed = HashMap.newHashMap(declared.size());
		List<String> undeclared = new ArrayList<>();

		scan.apply(stdout, (name, outcome, durationMillis, detail) -> {
			if (!declared.containsKey(name)) {
				undeclared.add(name);
				return;
			}
			observed.merge(name, new Observation(outcome, durationMillis, detail), Observation::mergedWith);
		});

		reportUndeclared(undeclared);
		return observed;
	}

	/**
	 * Turns one declared test and what the report said about it into a result.
	 * @param test the declared test
	 * @param observation what the report reported, or {@code null} if it never did
	 * @return the result carried forward to scoring and storage
	 */
	static ParsedResult toResult(Manifest.ManifestTest test, @Nullable Observation observation) {
		TestOutcome outcome = (observation != null) ? observation.outcome() : TestOutcome.NOT_EXECUTED;
		// Only a test that did not pass carries its diagnostic text forward, and even
		// then only for the instructor view; the redactor decides what a student may see.
		String detail = (observation != null && outcome != TestOutcome.PASSED) ? observation.detail() : null;
		Long durationMillis = (observation != null) ? observation.durationMillis() : null;
		BigDecimal weight = (test.weight() != null) ? test.weight() : BigDecimal.ONE;

		return new ParsedResult(TestResultRecord.VISIBILITY_HIDDEN, test.category(), test.name(), null, outcome, weight,
				durationMillis, null, detail, test.hint());
	}

	/**
	 * Shapes the declared tests into results in manifest order.
	 * @param manifest the operator's declaration of the suite
	 * @param observed what the report said, keyed by declared test name
	 * @return one result per declared test, in manifest order
	 */
	static List<ParsedResult> results(Manifest manifest, Map<String, Observation> observed) {
		return manifest.tests().stream().map((test) -> toResult(test, observed.get(test.name()))).toList();
	}

	/**
	 * Warns when the report named tests the manifest does not declare.
	 *
	 * <p>
	 * Almost always a manifest that drifted from the suite, and without the offending
	 * names an operator cannot tell which. They come from untrusted output, so they are
	 * stripped of control characters and truncated before reaching the log.
	 * @param undeclared every undeclared name the report reported
	 */
	private static void reportUndeclared(List<String> undeclared) {
		if (undeclared.isEmpty()) {
			return;
		}
		List<String> sample = undeclared.stream()
			.distinct()
			.limit(MAX_REPORTED_UNDECLARED)
			.map(ManifestJoin::sanitise)
			.toList();
		ManifestJoin.logger.warn("Ignored {} report line(s) naming tests the manifest does not declare; "
				+ "either the manifest has drifted from the suite or the submission wrote them itself. "
				+ "First names seen: {}", undeclared.size(), sample);
	}

	private static String sanitise(String name) {
		String stripped = name.replaceAll("\\p{Cntrl}", "");
		return (stripped.length() > MAX_REPORTED_NAME_LENGTH) ? stripped.substring(0, MAX_REPORTED_NAME_LENGTH) + "…"
				: stripped;
	}

	/**
	 * What the report said about one declared test.
	 *
	 * @param outcome how it ended
	 * @param durationMillis how long it took, when the report said
	 * @param detail the failure text, or the skipped reason
	 */
	record Observation(TestOutcome outcome, @Nullable Long durationMillis, @Nullable String detail) {

		/**
		 * Combines a repeated report of the same test.
		 *
		 * <p>
		 * A suite reports each test once, so a second report is either a manifest naming
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
	 * Receives each completed test the report described.
	 */
	@FunctionalInterface
	interface ReportedTest {

		/**
		 * Accepts one reported test.
		 * @param name the name the reporter emitted
		 * @param outcome how the test ended
		 * @param durationMillis how long it took, when the report said
		 * @param detail the failure text, or the skipped reason
		 */
		void accept(String name, TestOutcome outcome, @Nullable Long durationMillis, @Nullable String detail);

	}

}
