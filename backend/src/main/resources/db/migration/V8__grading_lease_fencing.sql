-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- Fence stale grading workers and protect the mutable submission status projection.

ALTER TABLE grading_jobs
	ADD COLUMN lease_generation BIGINT NOT NULL DEFAULT 0;

ALTER TABLE submissions
	ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN grading_jobs.lease_generation IS
	'Monotonic fencing token incremented for every claim. Only the current worker and generation may finish a job.';
COMMENT ON COLUMN submissions.version IS
	'Optimistic-lock version for the mutable latest-grading-status projection.';
COMMENT ON TABLE submissions IS
	'Historical submission facts are immutable. Status is a versioned mutable projection of the latest grading run.';
COMMENT ON TABLE grading_runs IS
	'Each regrade appends a run. A run advances through its lifecycle; completed result data is historical.';
