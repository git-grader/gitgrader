-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- Make the grading queue fair, and bound what one student can occupy.
--
-- THE PROBLEM
--   Nothing limited how much grading work a single student could create. Every push is a
--   distinct commit, so a loop of `git commit --allow-empty && git push` produced one
--   queued job per iteration, and the dispatcher drained them in a plain FIFO. One
--   student could therefore put hours of sandbox work in front of everyone else on the
--   course, without any single request being abusive in isolation.
--
--   Two things fix that, and both need columns the queue does not currently have:
--
--   COALESCING  Only the newest unstarted submission for a student and assignment is
--               worth grading. Older queued work is superseded and cancelled, so a burst
--               of pushes collapses to one job instead of accumulating.
--
--   FAIRNESS    The dispatcher must be able to pick one job per student and skip students
--               who already have work running. That decision has to happen inside the
--               claim query, which means the identity has to be on the row.
--
-- WHY THE IDENTITY IS DENORMALISED
--   Student, course and assignment all live on `submissions`. The claim query runs every
--   two seconds under `FOR UPDATE SKIP LOCKED`, and joining to `submissions` to discover
--   who a job belongs to would both widen the lock footprint and defeat the partial
--   indexes that keep the dispatch scan proportional to runnable work rather than to
--   accumulated history.
--
--   This follows the precedent set by V3: a queue row states its own identity instead of
--   depending on a join to establish it.

ALTER TABLE grading_jobs
	ADD COLUMN student_id    UUID,
	ADD COLUMN course_id     UUID,
	ADD COLUMN assignment_id UUID;

-- Every job already points at a submission with NOT NULL identity columns, so the
-- backfill is total and the NOT NULL below cannot fail.
UPDATE grading_jobs AS j
SET student_id    = s.student_id,
    course_id     = s.course_id,
    assignment_id = s.assignment_id
FROM submissions AS s
WHERE s.id = j.submission_id;

ALTER TABLE grading_jobs
	ALTER COLUMN student_id    SET NOT NULL,
	ALTER COLUMN course_id     SET NOT NULL,
	ALTER COLUMN assignment_id SET NOT NULL;

ALTER TABLE grading_jobs
	ADD CONSTRAINT grading_jobs_student_fk
		FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
	ADD CONSTRAINT grading_jobs_course_fk
		FOREIGN KEY (course_id) REFERENCES courses (id) ON DELETE CASCADE,
	ADD CONSTRAINT grading_jobs_assignment_fk
		FOREIGN KEY (assignment_id) REFERENCES assignments (id) ON DELETE CASCADE;

-- Apply the coalescing rule retroactively.
--
-- The partial unique index below cannot be created while an existing deployment still
-- holds several pending jobs for one student and assignment, which is exactly the state
-- this migration exists to make impossible. Keep the newest and cancel the rest, which is
-- the same decision the orchestrator will now make on every push. A job that is already
-- CLAIMED or RUNNING is deliberately untouched: work in a sandbox is left to finish.
WITH superseded AS (
	SELECT id, grading_run_id, submission_id
	FROM (
		SELECT id,
		       grading_run_id,
		       submission_id,
		       row_number() OVER (
		           PARTITION BY student_id, assignment_id
		           ORDER BY created_at DESC, id DESC
		       ) AS position
		FROM grading_jobs
		WHERE status = 'PENDING'
	) ranked
	WHERE ranked.position > 1
),
cancelled_jobs AS (
	UPDATE grading_jobs
	SET status = 'CANCELLED', finished_at = now(), updated_at = now()
	WHERE id IN (SELECT id FROM superseded)
),
cancelled_runs AS (
	UPDATE grading_runs
	SET status = 'CANCELLED', finished_at = now()
	WHERE id IN (SELECT grading_run_id FROM superseded)
	  AND status = 'QUEUED'
)
UPDATE submissions
SET status = 'CANCELLED'
WHERE id IN (SELECT submission_id FROM superseded)
  AND status IN ('RECEIVED', 'QUEUED');

-- One unstarted job per student and assignment, enforced by the database rather than by
-- the orchestrator remembering to check. Concurrent pushes from two instances race to
-- insert; this index is what makes the loser fail rather than double-queue.
CREATE UNIQUE INDEX grading_jobs_one_pending_per_assignment_idx
	ON grading_jobs (student_id, assignment_id)
	WHERE status = 'PENDING';

-- Supports the per-student head-of-queue selection in the claim query.
CREATE INDEX grading_jobs_student_dispatch_idx
	ON grading_jobs (student_id, priority, available_at)
	WHERE status = 'PENDING';

-- Supports the anti-join that excludes students who already occupy a worker.
CREATE INDEX grading_jobs_student_active_idx
	ON grading_jobs (student_id)
	WHERE status IN ('CLAIMED', 'RUNNING');

-- Supports the per-course and per-student pending caps checked at enqueue time.
CREATE INDEX grading_jobs_course_pending_idx
	ON grading_jobs (course_id)
	WHERE status = 'PENDING';

COMMENT ON COLUMN grading_jobs.student_id IS
	'Denormalised from the submission so the claim query can schedule fairly without joining. See V4.';
COMMENT ON COLUMN grading_jobs.course_id IS
	'Denormalised from the submission so per-course queue caps can be checked without joining. See V4.';
COMMENT ON COLUMN grading_jobs.assignment_id IS
	'Denormalised from the submission so newest-wins coalescing can be enforced by a partial unique index. See V4.';
COMMENT ON INDEX grading_jobs_one_pending_per_assignment_idx IS
	'Newest push wins: a student never has more than one unstarted grading job per assignment.';
