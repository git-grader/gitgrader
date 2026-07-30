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

package org.gitgrader.audit;

/**
 * The catalogue of auditable actions.
 *
 * <p>
 * Modelled as an enum rather than a free string so that a typo cannot silently create a
 * new, unqueryable event type and so that a retention policy can be expressed against a
 * closed set.
 */
public enum AuditEventType {

	/** A student completed the public self-service registration form. */
	STUDENT_REGISTERED,

	/** An instructor raised a profile to {@code VERIFIED_BY_INSTRUCTOR}. */
	STUDENT_VERIFIED,

	/** A profile was suspended and can no longer push. */
	STUDENT_SUSPENDED,

	/** A profile was archived at the end of a course. */
	STUDENT_ARCHIVED,

	/** A profile was anonymised in response to a data protection request. */
	STUDENT_ANONYMIZED,

	/** A public key was attached to a student. */
	SSH_KEY_ADDED,

	/** A public key was superseded by another key. */
	SSH_KEY_REPLACED,

	/** A public key was revoked and can no longer authenticate or sign. */
	SSH_KEY_REVOKED,

	/** A previously revoked or suspended key was reinstated. */
	SSH_KEY_REINSTATED,

	/** An instructor or administrator authenticated successfully. */
	LOGIN_SUCCEEDED,

	/** An authentication attempt was rejected. */
	LOGIN_FAILED,

	/** A web session was ended by the user. */
	LOGOUT,

	/** A course was created or modified. */
	COURSE_CHANGED,

	/** An assignment was created or modified. */
	ASSIGNMENT_CHANGED,

	/** An assignment moved out of {@code DRAFT} and became reachable. */
	ASSIGNMENT_PUBLISHED,

	/** An assignment schedule was changed. */
	DEADLINE_CHANGED,

	/** A per-student deadline extension was granted. */
	EXTENSION_GRANTED,

	/** A per-student deadline extension was withdrawn. */
	EXTENSION_REVOKED,

	/** A template version was published and became immutable. */
	TEMPLATE_PUBLISHED,

	/** A hidden test suite version was published. */
	TEST_SUITE_PUBLISHED,

	/** A runtime definition was registered or changed. */
	RUNTIME_CHANGED,

	/** A repository was provisioned for a student and assignment. */
	REPOSITORY_PROVISIONED,

	/** A push was accepted and recorded as a submission. */
	SUBMISSION_RECEIVED,

	/** A push was refused. The reason is always recorded alongside. */
	SUBMISSION_REJECTED,

	/** A grading run finished, successfully or not. */
	GRADING_COMPLETED,

	/** A grading run was started again by hand. */
	GRADING_RETRIED,

	/** A result token was invalidated. */
	RESULT_TOKEN_REVOKED,

	/** A report was exported. */
	REPORT_EXPORTED,

	/** A runtime-adjustable system setting was changed. */
	SETTING_CHANGED,

	/** A request was refused by a rate limiter. */
	RATE_LIMIT_TRIGGERED

}
