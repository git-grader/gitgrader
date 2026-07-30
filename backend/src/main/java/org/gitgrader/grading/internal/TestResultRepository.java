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
import java.util.UUID;

import org.gitgrader.grading.domain.TestResultRecord;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Persistence access for individual test outcomes.
 */
public interface TestResultRepository extends JpaRepository<TestResultRecord, UUID> {

	/**
	 * Lists the results of one grading run in display order.
	 *
	 * <p>
	 * Returns the full records, including hidden test names and raw assertion output.
	 * Callers serving a student must go through {@code TestResultRedactor} rather than
	 * exposing these directly.
	 * @param gradingRunId the run
	 * @return the run's test results
	 */
	List<TestResultRecord> findByGradingRunIdOrderByDisplayOrder(UUID gradingRunId);

}
