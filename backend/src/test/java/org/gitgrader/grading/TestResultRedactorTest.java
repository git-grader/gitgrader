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

package org.gitgrader.grading;

import java.math.BigDecimal;

import org.gitgrader.grading.internal.ParsedResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TestResultRedactorTest {

	@Test
	void hiddenTestIsRedactedForStudent() {
		ParsedResult result = new ParsedResult("HIDDEN", "Database Connection", "test_db_disconnects_properly", null,
				TestOutcome.FAILED, BigDecimal.ONE, 150L, "Student safe message",
				"org.postgresql.util.PSQLException: Connection refused at /var/lib/runner/test.java:45",
				"Check if you close connections in the finally block.");

		StudentTestResultView studentView = TestResultRedactor.toStudentView(result);

		assertThat(studentView.visibility()).isEqualTo("HIDDEN");
		assertThat(studentView.category()).isEqualTo("Database Connection");
		assertThat(studentView.hint()).isEqualTo("Check if you close connections in the finally block.");
		assertThat(studentView.outcome()).isEqualTo(TestOutcome.FAILED);
		assertThat(studentView.durationMs()).isEqualTo(150L);

		// Assert it's completely missing real test name and internal message
		assertThat(studentView.publicName()).isNull();
		assertThat(studentView.studentMessage()).isNull();

		InstructorTestResultView instructorView = TestResultRedactor.toInstructorView(result);
		assertThat(instructorView.testName()).isEqualTo("test_db_disconnects_properly");
		assertThat(instructorView.internalMessage()).contains("/var/lib/runner");
	}

	@Test
	void publicTestKeepsInformation() {
		ParsedResult result = new ParsedResult("PUBLIC", "Basic math", "test_addition", "Addition Test",
				TestOutcome.PASSED, BigDecimal.ONE, 10L, "You passed addition", "Everything worked", "No hint");

		StudentTestResultView studentView = TestResultRedactor.toStudentView(result);

		assertThat(studentView.publicName()).isEqualTo("Addition Test");
		assertThat(studentView.studentMessage()).isEqualTo("You passed addition");
		assertThat(studentView.outcome()).isEqualTo(TestOutcome.PASSED);
	}

	@Test
	void logsAreNotSafeForStudent() {
		assertThat(TestResultRedactor.isLogSafeForStudent()).isFalse();
	}

}
