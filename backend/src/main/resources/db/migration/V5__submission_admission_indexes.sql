-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- Support durable submission admission.
--
-- Two checks now run inside the transaction that records a push: the same commit must not
-- already have been submitted, and a student must not have exceeded their rolling hourly
-- push allowance. Both have to be answered from the database rather than from an
-- in-process counter, because the limits must survive a restart and hold across the
-- second instance an operator is told they may start for extra grading capacity.
--
-- The duplicate check is already served by `submissions_unique_commit`, whose btree on
-- (repository_id, commit_sha, received_at) answers a lookup on its leading columns. The
-- rolling window is not: `submissions_student_idx` is (student_id, received_at DESC) and
-- would have to filter every one of a student's submissions across all assignments to
-- answer a per-assignment question.

CREATE INDEX submissions_student_assignment_received_idx
	ON submissions (student_id, assignment_id, received_at DESC);

COMMENT ON INDEX submissions_student_assignment_received_idx IS
	'Serves the rolling per-assignment push window checked when a submission is admitted. See V5.';
