-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0

-- Honours the per-test weights a manifest has always been able to declare.
--
-- The weight was parsed, persisted on every test result and then ignored by the scorer, which
-- divided the number of passing tests by the number of tests. A manifest that made one check
-- worth five times another scored exactly the same as one that weighted everything equally,
-- so the field was documentation the application did not keep.
--
-- DEFAULT FALSE is the whole point of this migration. Every assignment that already exists
-- keeps the equal-weight formula, so no grade that has been issued, and no submission
-- already queued for grading, can change meaning because of this release. Only assignments
-- created after it are weighted.
--
-- This is also safe for the assignments it does affect: a manifest that declares no weights
-- gives every test a weight of one, and a weighted score over a set of ones is arithmetically
-- identical to the count-based score. The only assignments whose number can move are those
-- whose manifests actually asked for unequal weights, which is what they were asking for.
ALTER TABLE assignments ADD COLUMN weights_enabled BOOLEAN NOT NULL DEFAULT FALSE;

COMMENT ON COLUMN assignments.weights_enabled IS
	'TRUE scores each test by the weight its manifest declared. FALSE scores every test equally. False for every assignment that existed before this column, so released grades keep their original meaning.';
