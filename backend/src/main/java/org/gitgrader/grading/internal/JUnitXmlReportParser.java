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

import java.io.IOException;
import java.io.StringReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.gitgrader.grading.TestOutcome;
import org.gitgrader.grading.domain.TestResultRecord;
import org.gitgrader.runtimes.RuntimeReportFormat;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

/**
 * Parses JUnit XML reports, as emitted by pytest's JUnit-XML plugin and by Maven Surefire
 * / Gradle test tasks.
 *
 * <p>
 * The manifest, not the output, decides which tests exist. That is a grading integrity
 * boundary rather than a convenience: the sandbox merges the reporter's XML with
 * everything the student's own code writes to standard output, so a submission containing
 * {@code print('<testcase name="free marks"/>')} emits a fragment indistinguishable from
 * the reporter's. Scoring whatever parsed let a student mint passing tests and dilute the
 * suite until the percentage said what they wanted.
 *
 * <p>
 * So exactly one result is produced per declared test, in manifest order, and a
 * {@code <testcase>} naming anything else is discarded. A declared test the report never
 * named is {@code NOT_EXECUTED} - a suite that dies halfway is scored on what it was
 * supposed to run, not on what survived. A declared test reported more than once is not
 * counted as passed at all: a suite reports each test once, so a second element is either
 * a broken manifest or forgery, and neither should earn marks.
 *
 * <p>
 * The grammar is matched with a streaming SAX handler rather than a DOM tree: the output
 * is untrusted and unbounded, and a submission can print XML until the disk fills. A
 * malformed fragment outside the reporter's envelope cannot corrupt the parse - any
 * element that is not a {@code <testsuite>} or a {@code <testcase>} simply carries
 * nothing that can be scored.
 */
@Component
public class JUnitXmlReportParser implements ReportParser {

	/**
	 * How many undeclared names are named in the warning that reports them.
	 */
	private static final int MAX_REPORTED_UNDECLARED = 5;

	/**
	 * Ceiling on one undeclared name in that warning; the output is untrusted.
	 */
	private static final int MAX_REPORTED_NAME_LENGTH = 80;

	/**
	 * JUnit XML reports seconds; the rest of the module keeps milliseconds.
	 */
	private static final long MILLIS_PER_SECOND = 1000;

	/** Feature id that stops an untrusted report from declaring a DOCTYPE. */
	private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

	private static final Logger logger = LoggerFactory.getLogger(JUnitXmlReportParser.class);

	@Override
	public boolean supports(RuntimeReportFormat format) {
		return RuntimeReportFormat.JUNIT_XML == format;
	}

	@Override
	public List<ParsedResult> parse(String stdout, String stderr, Manifest manifest) {
		Map<String, Manifest.ManifestTest> declared = declaredTests(manifest);
		Map<String, Observation> observed = HashMap.newHashMap(declared.size());
		List<String> undeclared = new ArrayList<>();

		collect(stdout, (name, outcome, durationMillis, detail) -> {
			if (!declared.containsKey(name)) {
				undeclared.add(name);
				return;
			}
			observed.merge(name, new Observation(outcome, durationMillis, detail), Observation::mergedWith);
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
	 * Reads every testcase out of the report, handing each completed one to the
	 * collector.
	 * <p>
	 * Parsing stops at the first malformed fragment: everything before it was a real
	 * report from the runner, everything after it is untrustworthy, and pretending the
	 * rest parsed would score a submission on half a stream.
	 * @param stdout everything the sandbox wrote to standard output
	 * @param collector receives one call per reported testcase
	 */
	private static void collect(String stdout, ReportedTest collector) {
		scan(new StringReader(stdout), new TestcaseCollector(collector));
	}

	/**
	 * Walks the XML stream with a SAX handler that turns each completed
	 * {@code <testcase>} into one report.
	 * <p>
	 * Parsing stops at the first malformed fragment: everything before it was a real
	 * report from the runner, everything after it is untrustworthy, and pretending the
	 * rest parsed would score a submission on half a stream. What was collected before
	 * the stream broke still counts - a truncated report is scored on its complete
	 * testcases, and the declared tests it never reached come back not executed.
	 * @param reader the output to read
	 * @param handler the handler collecting testcases
	 */
	private static void scan(StringReader reader, TestcaseCollector handler) {
		var factory = SAXParserFactory.newInstance();
		factory.setNamespaceAware(false);
		try {
			// The report is untrusted output, so the parser must not resolve external
			// entities (XXE) and must not expand entity references; the grammar the
			// reporters emit needs neither.
			factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
			factory.setFeature(DISALLOW_DOCTYPE, true);
			factory.newSAXParser().parse(new InputSource(reader), handler);
		}
		catch (IOException | ParserConfigurationException | SAXException | RuntimeException expected) {
			// The stream broke or was never XML at all; the handler keeps what it
			// collected before the break, which is all of the report that is trustworthy.
			handler.complete();
		}
	}

	/**
	 * Turns one declared test and what the report said about it into a result.
	 * @param test the declared test
	 * @param observation what the report reported, or {@code null} if it never did
	 * @return the result carried forward to scoring and storage
	 */
	private static ParsedResult toResult(Manifest.ManifestTest test, @Nullable Observation observation) {
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
			.map(JUnitXmlReportParser::sanitise)
			.toList();
		logger.warn("Ignored {} JUnit XML testcase element(s) naming tests the manifest does not declare; "
				+ "either the manifest has drifted from the suite or the submission wrote them itself. "
				+ "First names seen: {}", undeclared.size(), sample);
	}

	private static String sanitise(String name) {
		String stripped = name.replaceAll("\\p{Cntrl}", "");
		return (stripped.length() > MAX_REPORTED_NAME_LENGTH) ? stripped.substring(0, MAX_REPORTED_NAME_LENGTH) + "…"
				: stripped;
	}

	/**
	 * Receives each testcase the JUnit XML report described.
	 */
	@FunctionalInterface
	private interface ReportedTest {

		/**
		 * Accepts one reported testcase.
		 * @param name the testcase's name attribute
		 * @param outcome how the testcase ended
		 * @param durationMillis how long it took, when the report said
		 * @param detail the child failure/error text, or the skipped reason
		 */
		void accept(String name, TestOutcome outcome, @Nullable Long durationMillis, @Nullable String detail);

	}

	/**
	 * What the report said about one declared test.
	 *
	 * @param outcome how it ended
	 * @param durationMillis how long it took, when the report said
	 * @param detail the failure text or skipped reason
	 */
	private record Observation(TestOutcome outcome, @Nullable Long durationMillis, @Nullable String detail) {

		/**
		 * Combines a repeated report of the same test.
		 *
		 * <p>
		 * A suite reports each test once, so a second element is either a manifest naming
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
	 * SAX handler that maps a JUnit XML stream onto {@link ReportedTest} callbacks.
	 *
	 * <p>
	 * JUnit XML describes a testcase as one element whose children say how it ended - no
	 * child means passed, {@code <failure>} or {@code <error>} means failed,
	 * {@code <skipped>} means not executed - so the handler holds the open testcase's
	 * state between callbacks and emits it exactly once, at the element's end tag.
	 */
	private static final class TestcaseCollector extends DefaultHandler {

		private final StringBuilder text = new StringBuilder();

		private final ReportedTest collector;

		private @Nullable String name;

		private @Nullable TestOutcome outcome;

		private @Nullable Long durationMillis;

		private @Nullable String detail;

		private boolean inFailure;

		private boolean open;

		private boolean done;

		TestcaseCollector(ReportedTest collector) {
			this.collector = collector;
		}

		@Override
		public void startElement(String uri, String localName, String qualifiedName, Attributes attributes) {
			String element = elementName(qualifiedName);
			if ("testcase".equals(element)) {
				flushOpen();
				this.name = attributes.getValue("name");
				this.outcome = TestOutcome.PASSED;
				this.durationMillis = parseDuration(attributes.getValue("time"));
				this.detail = null;
				this.inFailure = false;
				this.open = true;
			}
			else if (this.open) {
				// Only child elements of an open testcase change its outcome; a
				// <testsuite> that drifted inside another one is still the child's
				// business.
				if ("failure".equals(element) || "error".equals(element)) {
					this.outcome = TestOutcome.FAILED;
					this.inFailure = true;
					this.text.setLength(0);
				}
				else if ("skipped".equals(element)) {
					this.outcome = TestOutcome.NOT_EXECUTED;
				}
			}
		}

		@Override
		public void characters(char[] characters, int start, int length) {
			if (this.inFailure) {
				this.text.append(characters, start, length);
			}
		}

		@Override
		public void endElement(String uri, String localName, String qualifiedName) {
			String element = elementName(qualifiedName);
			if ("failure".equals(element) || "error".equals(element)) {
				this.detail = this.text.toString().trim();
				this.inFailure = false;
			}
			else if ("testcase".equals(element)) {
				flushOpen();
			}
		}

		/**
		 * Marks the stream finished, so a truncated report still emits its last complete
		 * testcase.
		 */
		void complete() {
			if (!this.done) {
				// Flush before the flag: the guard below refuses work once the stream is
				// over, so the last open testcase must be emitted first.
				flushOpen();
				this.done = true;
			}
		}

		private void flushOpen() {
			String reportedName = this.name;
			TestOutcome reportedOutcome = this.outcome;
			if (!this.open || this.done || reportedName == null || reportedOutcome == null) {
				return;
			}
			this.collector.accept(reportedName, reportedOutcome, this.durationMillis, this.detail);
			this.open = false;
			this.name = null;
			this.outcome = null;
			this.durationMillis = null;
			this.detail = null;
		}

		private static String elementName(String qualifiedName) {
			int colon = qualifiedName.indexOf(':');
			return (colon >= 0) ? qualifiedName.substring(colon + 1) : qualifiedName;
		}

		private static @Nullable Long parseDuration(@Nullable String raw) {
			if (raw == null) {
				return null;
			}
			try {
				return Math.round(Double.parseDouble(raw) * MILLIS_PER_SECOND);
			}
			catch (NumberFormatException expected) {
				// A malformed duration is cosmetic; it must never fail a grading run.
				return null;
			}
		}

	}

}
