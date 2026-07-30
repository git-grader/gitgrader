-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- Carry the repository path on the submission itself.
--
-- WHY THIS COLUMN EXISTS
--   The grading module has to find the bare repository holding the pushed commit. The
--   path lives in `repositories`, which is owned by the `git` module - but `git` already
--   depends on `grading` (it calls it to grade a push), so a `grading -> git` dependency
--   would close a cycle and Spring Modulith would reject the build.
--
--   Denormalising the path onto the submission breaks the cycle without weakening the
--   boundary: `git` writes it, `grading` reads it, and both already depend on
--   `submissions`. It also happens to be the more correct record: a submission is a
--   historical fact, and it should state where its objects were without depending on a
--   mutable row elsewhere still existing.
--
-- WHY IT IS NULLABLE
--   Submissions recorded before this migration have no value and must keep loading. The
--   grading dispatcher treats a null as "cannot reproduce this run" rather than guessing.

ALTER TABLE submissions
	ADD COLUMN repository_path TEXT;

COMMENT ON COLUMN submissions.repository_path IS
	'Repository path relative to the repository root, captured at push time so that a grading run never has to resolve it through another module.';
