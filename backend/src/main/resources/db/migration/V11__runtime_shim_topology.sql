-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- A runtime may grade the submission and the hidden suite in two separate containers
-- bound by the grading runtime protocol (issue #40). Whether it does, and with which
-- shim server command, belongs to the runtime record so the choice survives restarts
-- instead of living in the runner's switch.
--
-- Both columns are nullable: NULL shim_kind selects the legacy single-container run,
-- so every existing runtime stays exactly as it was before this migration. NULL
-- shim_command selects the canonical command baked into the runtime image
-- (node /opt/gitgrader-shim/server.js), so an operator that only wants the topology
-- still writes one column.

ALTER TABLE runtimes ADD COLUMN shim_kind TEXT;
ALTER TABLE runtimes ADD COLUMN shim_command TEXT;

COMMENT ON COLUMN runtimes.shim_kind IS
	'Topology key splitting a run into a sandbox and a suite bound by the grading '
	'protocol (issue #40); NULL keeps the legacy single-container run.';
COMMENT ON COLUMN runtimes.shim_command IS
	'Command that starts the shim server inside the sandbox; NULL uses the canonical '
	'command baked into the runtime image at /opt/gitgrader-shim.';