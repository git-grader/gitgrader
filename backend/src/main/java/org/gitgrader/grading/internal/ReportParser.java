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

import org.gitgrader.runtimes.ReportFormat;

/**
 * Parses test runner output into structured test results.
 */
public interface ReportParser {

	/**
	 * Tells whether this parser can read the given report format.
	 *
	 * <p>
	 * A runtime stores the format its reporter emits, but the formats the enum names are
	 * not all implemented here. Parsing the wrong grammar does not fail: it matches
	 * nothing, every declared test comes back unexecuted, and the scorer divides those
	 * into a confident zero. That is indistinguishable on a student's page from a student
	 * who passed nothing, so a format with no parser is refused instead of guessed at.
	 * @param format the format a runtime declares
	 * @return whether this parser implements that format
	 */
	boolean supports(ReportFormat format);

	/**
	 * Parses a report from the test runner.
	 * @param stdout the standard output from the runner
	 * @param stderr the standard error from the runner
	 * @param manifest the test suite manifest
	 * @return the list of parsed test results
	 */
	List<ParsedResult> parse(String stdout, String stderr, Manifest manifest);

}
