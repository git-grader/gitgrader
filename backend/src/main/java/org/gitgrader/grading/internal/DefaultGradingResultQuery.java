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
import java.util.Optional;
import java.util.UUID;

import org.gitgrader.grading.GradingResultQuery;
import org.gitgrader.grading.StudentGradingResult;
import org.gitgrader.grading.StudentTestResultView;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.grading.domain.TestResultRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads a submission's latest grading outcome for the student who submitted it.
 */
@Service
class DefaultGradingResultQuery implements GradingResultQuery {

	private final GradingRunRepository runs;

	private final TestResultRepository results;

	DefaultGradingResultQuery(GradingRunRepository runs, TestResultRepository results) {
		this.runs = runs;
		this.results = results;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<StudentGradingResult> findLatestForSubmission(UUID submissionId) {
		return this.runs.findFirstBySubmissionIdOrderByAttemptDesc(submissionId).map(this::toStudentResult);
	}

	private StudentGradingResult toStudentResult(GradingRun run) {
		List<StudentTestResultView> tests = this.results.findByGradingRunIdOrderByDisplayOrder(run.id())
			.stream()
			.map(DefaultGradingResultQuery::toStudentView)
			.toList();
		return new StudentGradingResult(run.status(), run.testsPassed(), run.testsTotal(), run.scorePercent(),
				run.passed(), tests);
	}

	/**
	 * Narrows a stored result to the columns a student may see.
	 *
	 * <p>
	 * A record keeps both what the check is really called and what the student is told it
	 * is, and likewise both messages. Only the public side of each pair is read here.
	 * Reading {@code testName} or {@code internalMessage} instead would hand the suite to
	 * anyone holding a result link, which is the whole reason the two are stored apart.
	 *
	 * <p>
	 * For a hidden check the student-facing message column holds the manifest's hint,
	 * which is the one thing such a failure is able to tell anyone. It is returned as the
	 * hint rather than as a message, because that is what it is and what the result page
	 * shows for a hidden check - returning it as a message left the hint field empty and
	 * meant no student ever saw one.
	 * @param record the stored result
	 * @return the student-facing view
	 */
	private static StudentTestResultView toStudentView(TestResultRecord record) {
		boolean hidden = TestResultRecord.VISIBILITY_HIDDEN.equals(record.visibility());
		return new StudentTestResultView(record.visibility(), record.category(), record.publicName(), record.outcome(),
				record.durationMs(), hidden ? null : record.studentMessage(), hidden ? record.studentMessage() : null);
	}

}
