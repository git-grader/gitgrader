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

/**
 * Parses test runner output into structured test results.
 */
public interface ReportParser {

	/**
	 * Parses a report from the test runner.
	 * @param stdout the standard output from the runner
	 * @param stderr the standard error from the runner
	 * @param manifest the test suite manifest
	 * @return the list of parsed test results
	 */
	List<ParsedResult> parse(String stdout, String stderr, Manifest manifest);

}
