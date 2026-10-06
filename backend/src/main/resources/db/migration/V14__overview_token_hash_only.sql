-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0

-- Stores the student results overview token as a hash instead of in plaintext.
--
-- This table was the only one of the two token tables that kept the usable value: every push
-- re-showed the same link, and re-showing requires the plaintext, because a hash is one-way.
-- That is why the value was kept at rest, and it is why a backup or a pg_dump disclosure was
-- a durable credential to a student's entire results page rather than merely an identifier.
--
-- The trade made here is to stop re-showing: the token is now rotated on every push and only
-- its hash is kept, which removes the plaintext while leaving resolution working. A link
-- handed out before this migration keeps working until that student's next push, and stops
-- working after it, because the next push revokes it. That is the intended behaviour of a
-- rotating token, and it is the reason this table can hold no usable credential.
--
-- The backfill expression must reproduce TokenHash.of() byte for byte: SHA-256 over the UTF-8
-- bytes, Base64 URL-safe alphabet, no padding. encode(..., 'base64') emits the standard
-- alphabet with '=' padding, so the translate() and rtrim() are what turn it into the Java
-- form. This was verified against a real server rather than assumed, across inputs whose
-- digests contain '+', '/', '-', '_' and multi-byte characters.
ALTER TABLE student_results_overview_tokens ADD COLUMN token_hash VARCHAR(64);

UPDATE student_results_overview_tokens
SET token_hash = translate(rtrim(encode(sha256(convert_to(token_value, 'UTF8')), 'base64'), '='), '+/', '-_')
WHERE token_hash IS NULL;

-- Only reached if a row predates the backfill, which cannot happen: the UPDATE covers every
-- row and token_value was NOT NULL. Stated so a failure here is a loud integrity error rather
-- than a token that can never resolve.
ALTER TABLE student_results_overview_tokens ALTER COLUMN token_hash SET NOT NULL;

CREATE UNIQUE INDEX uq_srot_token_hash ON student_results_overview_tokens (token_hash);

-- The plaintext is no longer needed to resolve a token: the request carries the token, the
-- application hashes it, and the row is found by that hash. So outstanding links keep working
-- through this migration, which is what lets it run against a live database without asking
-- any student to re-request their link.
--
-- The column is kept rather than dropped. A DROP COLUMN cannot be reversed by a later
-- migration, and a nullable, permanently empty column for one release costs nothing while
-- leaving this change revertible without data loss. Nothing may write to it again.
--
-- DROP NOT NULL must run BEFORE the UPDATE: token_value was NOT NULL for every row that
-- predates this migration, and PostgreSQL enforces the constraint per statement, so setting
-- it to NULL first aborts on any non-empty table. A fresh database hides the order bug only
-- because its table is empty; against a live database the migration failed outright.
ALTER TABLE student_results_overview_tokens ALTER COLUMN token_value DROP NOT NULL;
UPDATE student_results_overview_tokens SET token_value = NULL;

COMMENT ON COLUMN student_results_overview_tokens.token_value IS
	'Retired: no usable token is stored here any more. Kept nullable and empty so this change stays revertible. Do not repopulate.';
COMMENT ON COLUMN student_results_overview_tokens.token_hash IS
	'Base64url-unpadded SHA-256 of the issued token; the only lookup key. Must match TokenHash.of().';
