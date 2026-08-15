-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- One position in a grading run holds one result.
--
-- `display_order` is the index of a check within its run, assigned 0..n-1 as the report
-- is parsed, so the pair is unique by construction and the table had nothing saying so.
-- A run written twice therefore landed a second full set of rows beside the first, and
-- the student's result page listed every check twice.
--
-- Writing a run twice was reachable: a claim is a lease taken once and never renewed, and
-- the reaper returns any job whose lease has run out without asking whether its worker is
-- still going, so a sandbox permitted to outlast its lease was requeued from underneath
-- itself and graded again. That is fixed where it starts, by holding the sandbox inside
-- the lease. This is the backstop, because an invariant the schema does not hold is one
-- regression away from being lost quietly.
--
-- The plain index this replaces covered the same two columns in the same order, so the
-- constraint's own index serves every lookup it did.

DO $$
DECLARE
	duplicates BIGINT;
BEGIN
	SELECT count(*) INTO duplicates FROM (
		SELECT grading_run_id, display_order FROM test_results
		GROUP BY grading_run_id, display_order HAVING count(*) > 1) AS d;
	IF duplicates > 0 THEN
		-- Refused rather than resolved. The duplicate sets come from two sandbox runs of
		-- the same submission, which are not required to agree, so choosing between them
		-- decides what a student was scored - an academic decision, not one a migration
		-- may take. Instances that never hit the defect have nothing to do here.
		RAISE EXCEPTION USING
			MESSAGE = format('%s grading run position(s) hold more than one result', duplicates),
			HINT = 'Resolve the duplicates before upgrading; see docs/upgrade.md.';
	END IF;
END $$;

DROP INDEX test_results_run_idx;

ALTER TABLE test_results ADD CONSTRAINT test_results_unique_position UNIQUE (grading_run_id, display_order);

COMMENT ON CONSTRAINT test_results_unique_position ON test_results IS
	'One position in a run holds one result. A run written twice would otherwise duplicate every check on the student''s result page.';
