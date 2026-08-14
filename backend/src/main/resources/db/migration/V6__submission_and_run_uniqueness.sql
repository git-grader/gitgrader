-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- Enforce in the schema three invariants the application already relies on.
--
-- 1. A commit is submitted to a repository once. `submissions_unique_commit` was named
--    for this and did not do it: including `received_at` meant the same commit pushed
--    twice a second apart produced two distinct keys, so the constraint accepted exactly
--    the duplicate it exists to refuse. The application check in DefaultSubmissionService
--    was therefore the only thing enforcing it, and an invariant with no backstop is one
--    regression away from being lost silently.
--
-- 2. A push is graded once. Spring Modulith replays an event publication that was not
--    marked complete, which happens whenever the process dies between the listener's
--    commit and that mark. The replay ran the whole enqueue path again, so one push
--    produced a second attempt, took a second sandbox, and replaced the result the
--    student had already been shown. Only a PUSH-triggered run is constrained; a regrade
--    is a deliberate second attempt and stays unrestricted.
--
-- 3. Shutdown finds the jobs a worker holds without scanning the queue. `claimed_by` had
--    no index, so handing back in-flight work read every row in the table.

DO $$
DECLARE
	duplicates BIGINT;
BEGIN
	SELECT count(*) INTO duplicates FROM (
		SELECT repository_id, commit_sha FROM submissions
		GROUP BY repository_id, commit_sha HAVING count(*) > 1) AS d;
	IF duplicates > 0 THEN
		-- Refused rather than resolved here. Deduplicating means deleting a recorded
		-- submission, and this project keeps submission history precisely so a disputed
		-- grade stays reconstructible; which of the duplicates to keep is an academic
		-- decision, not one a migration may take.
		RAISE EXCEPTION USING
			MESSAGE = format('%s repository/commit pair(s) have more than one submission', duplicates),
			HINT = 'Resolve the duplicates before upgrading; see docs/upgrade.md.';
	END IF;
END $$;

ALTER TABLE submissions DROP CONSTRAINT submissions_unique_commit;
ALTER TABLE submissions ADD CONSTRAINT submissions_unique_commit UNIQUE (repository_id, commit_sha);

CREATE UNIQUE INDEX grading_runs_one_push_per_submission_idx
	ON grading_runs (submission_id) WHERE trigger = 'PUSH';

CREATE INDEX grading_jobs_claimed_by_idx
	ON grading_jobs (claimed_by) WHERE status IN ('CLAIMED', 'RUNNING');

COMMENT ON CONSTRAINT submissions_unique_commit ON submissions IS
	'A commit is submitted to a repository once. Re-pushing it is refused at admission and, failing that, here.';
