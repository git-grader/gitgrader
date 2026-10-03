-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0

-- Persistent overview token per student so the "all results" link remains stable
-- across pushes and can be returned on every push without a login.

CREATE TABLE student_results_overview_tokens (
	id UUID NOT NULL,
	student_id UUID NOT NULL,
	token_value VARCHAR(128) NOT NULL,
	token_prefix TEXT NOT NULL,
	status VARCHAR(16) NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	expires_at TIMESTAMPTZ,
	last_used_at TIMESTAMPTZ,
	access_count BIGINT NOT NULL DEFAULT 0,
	CONSTRAINT pk_student_results_overview_tokens PRIMARY KEY (id),
	CONSTRAINT uq_srot_token_value UNIQUE (token_value),
	CONSTRAINT fk_srot_student FOREIGN KEY (student_id) REFERENCES students (id) ON DELETE CASCADE,
	CONSTRAINT chk_srot_status CHECK (status IN ('ACTIVE', 'REVOKED', 'EXPIRED'))
);

-- A student may accumulate one retired row per expiry, so student_id is deliberately not
-- unique; at most one row is ACTIVE at a time, which this index serves.
CREATE INDEX idx_srot_student_status ON student_results_overview_tokens (student_id, status);

COMMENT ON TABLE student_results_overview_tokens IS
	'Long-lived, read-only token granting access to a student''s results overview.';
COMMENT ON COLUMN student_results_overview_tokens.token_prefix IS
	'Leading characters only for correlation/support.';
COMMENT ON COLUMN student_results_overview_tokens.status IS
	'ACTIVE: can be used; REVOKED/EXPIRED: treated as invalid (404).';
