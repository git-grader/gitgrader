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
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.gitgrader.grading.GradingRunStatus;
import org.gitgrader.grading.InstructorGradingResult;
import org.gitgrader.grading.TestOutcome;
import org.gitgrader.grading.domain.GradingRun;
import org.gitgrader.grading.domain.TestResultRecord;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultGradingResultQueryTest {

	@Test
	void instructorResultKeepsSafeTestFactsAndOmitsHiddenCanaries() throws Exception {
		GradingRunRepository runs = mock(GradingRunRepository.class);
		TestResultRepository results = mock(TestResultRepository.class);
		DefaultGradingResultQuery query = new DefaultGradingResultQuery(runs, results);
		UUID submissionId = UUID.randomUUID();
		UUID runId = UUID.randomUUID();
		GradingRun run = mock(GradingRun.class);
		when(runs.findFirstBySubmissionIdOrderByAttemptDesc(submissionId)).thenReturn(Optional.of(run));
		when(run.id()).thenReturn(runId);
		when(run.attempt()).thenReturn(2);
		when(run.status()).thenReturn(GradingRunStatus.COMPLETED);
		when(run.testsPassed()).thenReturn(1);
		when(run.testsTotal()).thenReturn(2);
		when(run.scorePercent()).thenReturn(new BigDecimal("50.00"));
		when(run.pointsAwarded()).thenReturn(new BigDecimal("5.00"));
		when(run.passed()).thenReturn(false);
		when(run.finishedAt()).thenReturn(Instant.parse("2026-03-01T10:15:30Z"));
		TestResultRecord publicResult = new TestResultRecord(runId, "PUBLIC", null, "PUBLIC_INTERNAL_NAME",
				"Visible test", TestOutcome.PASSED, BigDecimal.ONE, 12L, "Visible feedback", "PUBLIC_INTERNAL_MESSAGE",
				0);
		TestResultRecord hiddenResult = new TestResultRecord(runId, "HIDDEN", "edge-case", "HIDDEN_NAME_CANARY",
				"HIDDEN_PUBLIC_NAME_CANARY", TestOutcome.FAILED, BigDecimal.ONE, 34L, "HIDDEN_STUDENT_MESSAGE_CANARY",
				"HIDDEN_INTERNAL_MESSAGE_CANARY", 1);
		when(results.findByGradingRunIdOrderByDisplayOrder(runId)).thenReturn(List.of(publicResult, hiddenResult));

		Optional<InstructorGradingResult> result = query.findLatestInstructorResultForSubmission(submissionId);
		String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(result.orElseThrow());

		assertThat(json).contains("Visible test", "Visible feedback", "edge-case", "FAILED", "34");
		assertThat(json).doesNotContain("HIDDEN_NAME_CANARY", "HIDDEN_PUBLIC_NAME_CANARY",
				"HIDDEN_STUDENT_MESSAGE_CANARY", "HIDDEN_INTERNAL_MESSAGE_CANARY", "PUBLIC_INTERNAL_NAME",
				"PUBLIC_INTERNAL_MESSAGE");
	}

}
