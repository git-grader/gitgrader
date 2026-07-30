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

package org.gitgrader.submissions;

import java.time.Instant;
import java.util.UUID;

/**
 * Published when a push has been accepted and permanently recorded.
 *
 * <p>
 * <strong>Why this is an event and not a method call.</strong> The natural shape of the
 * flow is {@code git} accepts a push, {@code submissions} stores it, {@code grading} runs
 * it, and {@code submissions} records the outcome. Expressed as direct calls that is a
 * dependency cycle. Expressed as an event it is a straight line: {@code submissions}
 * announces a fact and does not care who acts on it.
 *
 * <p>
 * The event is written to Spring Modulith's publication registry inside the same
 * transaction as the submission row. If the process dies before {@code grading} finishes
 * handling it, the publication is still incomplete on restart and is retried. That
 * property is what lets GitGrader survive a restart mid-grading without an external
 * message broker, which is the whole reason the platform needs only PostgreSQL.
 *
 * <p>
 * Carries identifiers only. A listener that needs detail reads it through the owning
 * module's API, so the payload cannot drift out of date between publication and delivery.
 *
 * @param submissionId the recorded submission
 * @param studentId who pushed
 * @param courseId owning course
 * @param assignmentId the assignment answered
 * @param commitSha the pushed commit
 * @param receivedAt server-side receive time
 * @param gradable whether the submission is eligible for grading at all; a refused push
 * is recorded but must never reach the sandbox
 */
public record SubmissionRecorded(UUID submissionId, UUID studentId, UUID courseId, UUID assignmentId, String commitSha,
		Instant receivedAt, boolean gradable) {

}
