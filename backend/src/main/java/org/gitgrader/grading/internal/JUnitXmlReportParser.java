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
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParserFactory;

import org.gitgrader.grading.TestOutcome;
import org.gitgrader.runtimes.RuntimeReportFormat;
import org.jspecify.annotations.Nullable;
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
 * The integrity rules - exactly one result per declared test, in manifest order, with
 * undeclared names discarded - live on {@link ManifestJoin}, which every parser shares.
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
	 * JUnit XML reports seconds; the rest of the module keeps milliseconds.
	 */
	private static final long MILLIS_PER_SECOND = 1000;

	/** Feature id that stops an untrusted report from declaring a DOCTYPE. */
	@SuppressWarnings("PMD.NoHardcodedPublicUrl") // a JAXP feature id, not a URL anyone
													// dials
	private static final String DISALLOW_DOCTYPE = "http://apache.org/xml/features/disallow-doctype-decl";

	@Override
	public boolean supports(RuntimeReportFormat format) {
		return RuntimeReportFormat.JUNIT_XML == format;
	}

	@Override
	public List<ParsedResult> parse(String stdout, String stderr, Manifest manifest) {
		Map<String, ManifestJoin.Observation> observed = ManifestJoin.observe(stdout, manifest,
				JUnitXmlReportParser::collect);
		return ManifestJoin.results(manifest, observed);
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
	private static Void collect(String stdout, ManifestJoin.ReportedTest collector) {
		scan(new StringReader(stdout), new TestcaseCollector(collector));
		return null;
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
	 * SAX handler that maps a JUnit XML stream onto {@link ManifestJoin.ReportedTest}
	 * callbacks.
	 *
	 * <p>
	 * JUnit XML describes a testcase as one element whose children say how it ended - no
	 * child means passed, {@code <failure>} or {@code <error>} means failed,
	 * {@code <skipped>} means not executed - so the handler holds the open testcase's
	 * state between callbacks and emits it exactly once, at the element's end tag.
	 */
	private static final class TestcaseCollector extends DefaultHandler {

		private final ManifestJoin.ReportedTest collector;

		private @Nullable String name;

		private @Nullable TestOutcome outcome;

		private @Nullable Long durationMillis;

		private @Nullable String detail;

		/**
		 * Collects character data of one open testcase's failure child. Bounded by the
		 * handler's own lifetime: a new handler is created per report, so the buffer
		 * never outlives one grading run, the same trade-off the TAP parser documents for
		 * its line buffer.
		 */
		@SuppressWarnings("PMD.AvoidStringBufferField")
		private final StringBuilder text = new StringBuilder();

		private boolean inFailure;

		private boolean open;

		private boolean done;

		TestcaseCollector(ManifestJoin.ReportedTest collector) {
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
