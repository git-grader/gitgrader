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

package org.gitgrader.identity.domain;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.gitgrader.identity.Actor;
import org.gitgrader.identity.ActorType;
import org.gitgrader.identity.IllegalStateTransitionException;
import org.gitgrader.identity.StudentRegistration;
import org.gitgrader.identity.StudentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for the {@link Student} status lifecycle and the eligibility it governs.
 *
 * <p>
 * Submission eligibility is derived from the status and the deployment's verification
 * policy together, so a self-registered account that is allowed to push in one
 * configuration and not in another must be expressible. Transitions the lifecycle does
 * not permit are rejected outright rather than ignored, so a second archive or a restore
 * from a live status cannot quietly rewrite history an instructor may be asked to account
 * for.
 *
 * <p>
 * Anonymisation is the other half: it has to leave the primary key intact, because that
 * key is what ties a redacted profile back to the submissions it produced, while the
 * personal fields become the identifier itself and the address is parked in a reserved
 * domain.
 */
class StudentTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-03-01T10:15:30Z"), ZoneOffset.UTC);

	private static final Actor INSTRUCTOR = new Actor(ActorType.INSTRUCTOR, "instructor-1", "Instructor");

	@Test
	@DisplayName("derives submission eligibility from the status and the verification policy")
	void submissionEligibilityFollowsStatusAndVerificationPolicy() {
		Student student = student();

		assertThat(student.canSubmit(false)).isTrue();
		assertThat(student.canSubmit(true)).isFalse();

		student.verify(INSTRUCTOR, CLOCK);
		assertThat(student.canSubmit(false)).isTrue();
		assertThat(student.canSubmit(true)).isTrue();

		student.suspend("policy violation", INSTRUCTOR, CLOCK);
		assertThat(student.canSubmit(false)).isFalse();
		assertThat(student.canSubmit(true)).isFalse();
	}

	@Test
	@DisplayName("rejects a transition the lifecycle does not allow")
	void illegalStateTransitionsAreRejected() {
		Student student = student();
		student.verify(INSTRUCTOR, CLOCK);

		assertThatThrownBy(() -> student.verify(INSTRUCTOR, CLOCK)).isInstanceOf(IllegalStateTransitionException.class);

		student.archive(CLOCK);
		assertThatThrownBy(() -> student.archive(CLOCK)).isInstanceOf(IllegalStateTransitionException.class);
		assertThatThrownBy(() -> student.suspend("reason", INSTRUCTOR, CLOCK))
			.isInstanceOf(IllegalStateTransitionException.class);
	}

	@Test
	@DisplayName("restores an archived profile")
	void archivedProfileCanBeRestored() {
		Student student = student();
		student.verify(INSTRUCTOR, CLOCK);
		student.archive(CLOCK);
		assertThat(student.toView().status()).isEqualTo(StudentStatus.ARCHIVED);
		assertThat(student.canSubmit(false)).isFalse();

		student.restore(INSTRUCTOR, CLOCK);
		assertThat(student.toView().status()).isEqualTo(StudentStatus.VERIFIED_BY_INSTRUCTOR);
		assertThat(student.canSubmit(false)).isTrue();
		assertThat(student.canSubmit(true)).isTrue();

		student.archive(CLOCK);
		assertThat(student.toView().status()).isEqualTo(StudentStatus.ARCHIVED);
	}

	@Test
	@DisplayName("rejects a restore for any state other than archived")
	void restoreIsRejectedForAnythingButAnArchivedProfile() {
		Student student = student();
		assertThatThrownBy(() -> student.restore(INSTRUCTOR, CLOCK))
			.isInstanceOf(IllegalStateTransitionException.class);

		student.verify(INSTRUCTOR, CLOCK);
		assertThatThrownBy(() -> student.restore(INSTRUCTOR, CLOCK))
			.isInstanceOf(IllegalStateTransitionException.class);

		student.suspend("reason", INSTRUCTOR, CLOCK);
		assertThatThrownBy(() -> student.restore(INSTRUCTOR, CLOCK))
			.isInstanceOf(IllegalStateTransitionException.class);
	}

	@Test
	@DisplayName("preserves the stable submission identifier through anonymization")
	void anonymizationPreservesStableSubmissionIdentifier() {
		Student student = student();
		var originalId = student.id();

		student.anonymize(CLOCK);

		assertThat(student.id()).isEqualTo(originalId);
		assertThat(student.toView().studentUsername()).contains(originalId.toString());
		assertThat(student.toView().email()).contains(originalId.toString()).endsWith("@invalid");
		assertThat(student.toView().fullName()).contains(originalId.toString());
		assertThat(student.anonymizedAt()).isEqualTo(Instant.now(CLOCK));
		assertThat(student.toView().status()).isEqualTo(StudentStatus.SELF_REGISTERED);
	}

	private static Student student() {
		return new Student(new StudentRegistration("S123", "Ada", "Lovelace", "ada@example.test", "A", "hash"), CLOCK);
	}

}
