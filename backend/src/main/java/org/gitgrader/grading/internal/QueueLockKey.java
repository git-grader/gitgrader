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

import java.util.UUID;

/**
 * Derives the advisory lock key that serialises queue decisions for one student and
 * assignment.
 *
 * <p>
 * Enqueueing and returning held work to the queue both read then write the pending job
 * for a pair: one looks for a job to supersede, the other for a job that already replaces
 * the one it is about to return. They have to take the same lock, or a shutdown can
 * requeue a job at the same moment a new push inserts its replacement and the partial
 * unique index turns the race into a constraint violation.
 *
 * <p>
 * A collision between two unrelated pairs only makes them take turns, which costs a
 * little contention and nothing else: the partial unique index, not this key, is what
 * guarantees a student never ends up with two queued runs for one assignment.
 */
final class QueueLockKey {

	/** Rotation applied to one half of the key so the two ids do not cancel out. */
	private static final int KEY_HALF_BITS = 32;

	private QueueLockKey() {
	}

	/**
	 * Builds the key for one student and assignment.
	 * @param studentId the student
	 * @param assignmentId the assignment
	 * @return a stable key for {@code pg_advisory_xact_lock}
	 */
	static long of(UUID studentId, UUID assignmentId) {
		long student = studentId.getMostSignificantBits() ^ studentId.getLeastSignificantBits();
		long assignment = assignmentId.getMostSignificantBits() ^ assignmentId.getLeastSignificantBits();
		return student ^ Long.rotateLeft(assignment, KEY_HALF_BITS);
	}

}
