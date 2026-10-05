-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0

-- Enforces the invariant the table comment already claimed and the plain index did not:
-- at most one ACTIVE row per student.
--
-- token_value was UNIQUE, so two concurrent pushes could both observe zero ACTIVE rows for
-- the same student and both insert, because nothing in the database stopped them. Once that
-- happened, every later issueForStudent and revoke called findByStudentIdAndStatus and got
-- two rows, which threw IncorrectResultSizeDataAccessException. That failure then recurred on
-- every subsequent push from that student, with no supported remedy, and the surplus token
-- stayed resolvable through revoke. The blast radius is one student per occurrence, but it is
-- permanent and self-inflicted, and it is reachable from ordinary use: two pushes of the same
-- repository a second apart.
--
-- Existing duplicates are removed before the index is created, because CREATE UNIQUE INDEX
-- fails outright on existing violations and a migration cannot be left half-applied on a live
-- database. Only surplus ACTIVE rows go, and the newest survives, so the link the student is
-- currently using keeps working. A duplicate in this table means the column was already
-- throwing on every push for that student, so nothing that works today depends on a row
-- removed here. Rows in other statuses are untouched: a student accumulates one retired row
-- per expiry by design.
DELETE FROM student_results_overview_tokens older
USING student_results_overview_tokens newer
WHERE older.student_id = newer.student_id
	AND older.status = 'ACTIVE'
	AND newer.status = 'ACTIVE'
	AND (older.created_at, older.id) < (newer.created_at, newer.id);

-- Partial, because only the ACTIVE row is exclusive; the retired history is not. This is the
-- constraint the plain (student_id, status) index was mistaken for: it makes lookups fast
-- without making the result unique.
CREATE UNIQUE INDEX uq_srot_one_active_per_student
	ON student_results_overview_tokens (student_id) WHERE status = 'ACTIVE';

COMMENT ON INDEX uq_srot_one_active_per_student IS
	'At most one ACTIVE overview token per student; enforced in the database so concurrent pushes cannot both mint one.';
