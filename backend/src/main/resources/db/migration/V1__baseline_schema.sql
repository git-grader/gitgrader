-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- GitGrader baseline schema.
--
-- CONVENTIONS
--   * Surrogate keys are UUID. They appear in URLs and log lines, so a sequential
--     integer would leak enrolment counts and submission volume.
--   * Every instant is TIMESTAMPTZ. The server side receive time of a push is legally
--     meaningful, so a naive local timestamp is not acceptable anywhere.
--   * Enumerations are stored as TEXT with a CHECK constraint rather than a native
--     PostgreSQL ENUM: adding a value stays a plain forward-compatible migration.
--   * Rows that carry history (ssh_keys, submissions, grading_runs, template_versions,
--     test_suite_versions) are never updated destructively and never deleted. This is
--     what makes an old submission still explainable after an upgrade.

-- =====================================================================================
-- IDENTITY
-- =====================================================================================

CREATE TABLE students (
	id                    UUID         PRIMARY KEY,
	student_number        TEXT         NOT NULL,
	first_name            TEXT         NOT NULL,
	last_name             TEXT         NOT NULL,
	email                 TEXT         NOT NULL,
	status                TEXT         NOT NULL DEFAULT 'SELF_REGISTERED',
	class_label           TEXT,
	notes                 TEXT,
	registered_at         TIMESTAMPTZ  NOT NULL,
	verified_at           TIMESTAMPTZ,
	verified_by           TEXT,
	suspended_at          TIMESTAMPTZ,
	suspension_reason     TEXT,
	archived_at           TIMESTAMPTZ,
	anonymized_at         TIMESTAMPTZ,
	last_seen_at          TIMESTAMPTZ,
	registration_ip_hash  TEXT,
	created_at            TIMESTAMPTZ  NOT NULL,
	updated_at            TIMESTAMPTZ  NOT NULL,
	version               BIGINT       NOT NULL DEFAULT 0,
	CONSTRAINT students_status_check
		CHECK (status IN ('SELF_REGISTERED', 'VERIFIED_BY_INSTRUCTOR', 'SUSPENDED', 'ARCHIVED'))
);

-- Case-insensitive uniqueness: a student who registers twice with different casing is
-- the same person, and letting both through would split their submission history.
CREATE UNIQUE INDEX students_student_number_key ON students (lower(student_number));
CREATE UNIQUE INDEX students_email_key          ON students (lower(email));
CREATE INDEX        students_status_idx         ON students (status);
CREATE INDEX        students_last_name_idx      ON students (lower(last_name), lower(first_name));

COMMENT ON COLUMN students.status IS
	'SELF_REGISTERED is the default and explicitly does NOT assert a verified identity.';
COMMENT ON COLUMN students.registration_ip_hash IS
	'Keyed hash of the registering address, kept for abuse investigation only. Never the raw address.';

-- Instructors and administrators authenticate against LDAP. This table is a local
-- projection created on first sign-in so that audit records, deadline extensions and key
-- revocations can reference a stable id even after a directory entry is renamed.
CREATE TABLE instructors (
	id             UUID         PRIMARY KEY,
	username       TEXT         NOT NULL,
	display_name   TEXT         NOT NULL,
	email          TEXT,
	roles          TEXT         NOT NULL DEFAULT 'INSTRUCTOR',
	first_login_at TIMESTAMPTZ  NOT NULL,
	last_login_at  TIMESTAMPTZ  NOT NULL,
	created_at     TIMESTAMPTZ  NOT NULL,
	updated_at     TIMESTAMPTZ  NOT NULL,
	version        BIGINT       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX instructors_username_key ON instructors (lower(username));

-- =====================================================================================
-- SSH KEYS
-- =====================================================================================

CREATE TABLE ssh_keys (
	id                UUID         PRIMARY KEY,
	student_id        UUID         NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
	label             TEXT         NOT NULL,
	key_type          TEXT         NOT NULL,
	public_key        TEXT         NOT NULL,
	fingerprint       TEXT         NOT NULL,
	key_bits          INTEGER,
	comment           TEXT,
	status            TEXT         NOT NULL DEFAULT 'ACTIVE',
	added_via         TEXT         NOT NULL DEFAULT 'REGISTRATION',
	added_by          TEXT,
	revoked_at        TIMESTAMPTZ,
	revoked_by        TEXT,
	revocation_reason TEXT,
	replaced_by_id    UUID         REFERENCES ssh_keys (id) ON DELETE SET NULL,
	last_used_at      TIMESTAMPTZ,
	created_at        TIMESTAMPTZ  NOT NULL,
	updated_at        TIMESTAMPTZ  NOT NULL,
	version           BIGINT       NOT NULL DEFAULT 0,
	CONSTRAINT ssh_keys_status_check
		CHECK (status IN ('ACTIVE', 'REVOKED', 'REPLACED', 'SUSPENDED')),
	CONSTRAINT ssh_keys_added_via_check
		CHECK (added_via IN ('REGISTRATION', 'SELF_SERVICE_SSH', 'INSTRUCTOR', 'ADMIN')),
	-- A revoked or replaced key must say when it stopped being valid, otherwise the
	-- signature attribution of an old submission cannot be reconstructed.
	CONSTRAINT ssh_keys_revocation_consistency
		CHECK ((status IN ('REVOKED', 'REPLACED')) = (revoked_at IS NOT NULL))
);

-- A fingerprint identifies a key globally. Two students must never share one, because
-- the SSH transport resolves the pushing student from exactly this value.
CREATE UNIQUE INDEX ssh_keys_fingerprint_key ON ssh_keys (fingerprint);
CREATE INDEX ssh_keys_student_idx            ON ssh_keys (student_id);
CREATE INDEX ssh_keys_active_idx             ON ssh_keys (fingerprint) WHERE status = 'ACTIVE';

COMMENT ON TABLE ssh_keys IS
	'Append-only. Keys are revoked or replaced, never deleted, so that a submission signed years ago still resolves to the key that signed it.';
COMMENT ON COLUMN ssh_keys.public_key IS
	'OpenSSH authorized_keys format. A private key is rejected before it can reach this column.';

-- =====================================================================================
-- COURSES AND CLASSES
-- =====================================================================================

CREATE TABLE courses (
	id                    UUID         PRIMARY KEY,
	course_key            TEXT         NOT NULL,
	name                  TEXT         NOT NULL,
	description           TEXT,
	semester              TEXT,
	starts_on             DATE,
	ends_on               DATE,
	timezone              TEXT         NOT NULL DEFAULT 'UTC',
	status                TEXT         NOT NULL DEFAULT 'DRAFT',
	registration_opens_at TIMESTAMPTZ,
	registration_closes_at TIMESTAMPTZ,
	registration_enabled  BOOLEAN      NOT NULL DEFAULT TRUE,
	created_at            TIMESTAMPTZ  NOT NULL,
	updated_at            TIMESTAMPTZ  NOT NULL,
	version               BIGINT       NOT NULL DEFAULT 0,
	CONSTRAINT courses_status_check
		CHECK (status IN ('DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED')),
	CONSTRAINT courses_date_order_check
		CHECK (starts_on IS NULL OR ends_on IS NULL OR starts_on <= ends_on)
);

-- The course key becomes a path segment in every clone URL, so it must be unique and
-- filesystem safe.
CREATE UNIQUE INDEX courses_course_key_key ON courses (lower(course_key));
CREATE INDEX        courses_status_idx     ON courses (status);

ALTER TABLE courses ADD CONSTRAINT courses_course_key_format
	CHECK (course_key ~ '^[a-z0-9][a-z0-9._-]{0,62}[a-z0-9]$');

CREATE TABLE course_classes (
	id         UUID        PRIMARY KEY,
	course_id  UUID        NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
	class_key  TEXT        NOT NULL,
	name       TEXT        NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL,
	version    BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT course_classes_key_unique UNIQUE (course_id, class_key)
);

CREATE TABLE course_instructors (
	course_id     UUID        NOT NULL REFERENCES courses (id) ON DELETE CASCADE,
	instructor_id UUID        NOT NULL REFERENCES instructors (id) ON DELETE CASCADE,
	assigned_at   TIMESTAMPTZ NOT NULL,
	PRIMARY KEY (course_id, instructor_id)
);

CREATE TABLE enrollments (
	id         UUID        PRIMARY KEY,
	student_id UUID        NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
	course_id  UUID        NOT NULL REFERENCES courses (id) ON DELETE RESTRICT,
	class_id   UUID        REFERENCES course_classes (id) ON DELETE SET NULL,
	status     TEXT        NOT NULL DEFAULT 'ACTIVE',
	enrolled_at TIMESTAMPTZ NOT NULL,
	created_at TIMESTAMPTZ NOT NULL,
	updated_at TIMESTAMPTZ NOT NULL,
	version    BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT enrollments_unique UNIQUE (student_id, course_id),
	CONSTRAINT enrollments_status_check CHECK (status IN ('ACTIVE', 'WITHDRAWN', 'ARCHIVED'))
);

CREATE INDEX enrollments_course_idx ON enrollments (course_id);
CREATE INDEX enrollments_class_idx  ON enrollments (class_id);

-- =====================================================================================
-- RUNTIMES
-- =====================================================================================

CREATE TABLE runtimes (
	id              UUID        PRIMARY KEY,
	runtime_key     TEXT        NOT NULL,
	display_name    TEXT        NOT NULL,
	image           TEXT        NOT NULL,
	tag             TEXT        NOT NULL,
	image_digest    TEXT        NOT NULL,
	install_command TEXT,
	test_command    TEXT        NOT NULL,
	report_format   TEXT        NOT NULL DEFAULT 'JUNIT_XML',
	enabled         BOOLEAN     NOT NULL DEFAULT TRUE,
	created_at      TIMESTAMPTZ NOT NULL,
	updated_at      TIMESTAMPTZ NOT NULL,
	version         BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT runtimes_key_unique UNIQUE (runtime_key),
	CONSTRAINT runtimes_report_format_check
		CHECK (report_format IN ('JUNIT_XML', 'TAP', 'JSON_SUMMARY')),
	-- A moving tag would silently change how an old submission grades. Only an immutable
	-- digest is ever used to start a container.
	CONSTRAINT runtimes_digest_format CHECK (image_digest ~ '^sha256:[a-f0-9]{64}$'),
	CONSTRAINT runtimes_tag_not_latest CHECK (tag <> 'latest')
);

COMMENT ON COLUMN runtimes.image_digest IS
	'Immutable digest actually used to start the sandbox. The tag is documentation only.';

-- =====================================================================================
-- TEMPLATES AND HIDDEN TEST SUITES
-- =====================================================================================

CREATE TABLE project_templates (
	id           UUID        PRIMARY KEY,
	template_key TEXT        NOT NULL,
	name         TEXT        NOT NULL,
	description  TEXT,
	created_at   TIMESTAMPTZ NOT NULL,
	updated_at   TIMESTAMPTZ NOT NULL,
	version      BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT project_templates_key_unique UNIQUE (template_key)
);

CREATE TABLE template_versions (
	id             UUID        PRIMARY KEY,
	template_id    UUID        NOT NULL REFERENCES project_templates (id) ON DELETE RESTRICT,
	version_label  TEXT        NOT NULL,
	storage_path   TEXT        NOT NULL,
	content_hash   TEXT        NOT NULL,
	file_count     INTEGER     NOT NULL DEFAULT 0,
	total_bytes    BIGINT      NOT NULL DEFAULT 0,
	published_at   TIMESTAMPTZ,
	published_by   TEXT,
	created_at     TIMESTAMPTZ NOT NULL,
	CONSTRAINT template_versions_unique UNIQUE (template_id, version_label)
);

COMMENT ON TABLE template_versions IS
	'Immutable once published. A new version never rewrites repositories that were already provisioned.';

CREATE TABLE test_suites (
	id            UUID        PRIMARY KEY,
	suite_key     TEXT        NOT NULL,
	name          TEXT        NOT NULL,
	description   TEXT,
	created_at    TIMESTAMPTZ NOT NULL,
	updated_at    TIMESTAMPTZ NOT NULL,
	version       BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT test_suites_key_unique UNIQUE (suite_key)
);

CREATE TABLE test_suite_versions (
	id             UUID        PRIMARY KEY,
	suite_id       UUID        NOT NULL REFERENCES test_suites (id) ON DELETE RESTRICT,
	version_label  TEXT        NOT NULL,
	storage_path   TEXT        NOT NULL,
	content_hash   TEXT        NOT NULL,
	hidden_test_count  INTEGER NOT NULL DEFAULT 0,
	public_test_count  INTEGER NOT NULL DEFAULT 0,
	published_at   TIMESTAMPTZ,
	published_by   TEXT,
	created_at     TIMESTAMPTZ NOT NULL,
	CONSTRAINT test_suite_versions_unique UNIQUE (suite_id, version_label)
);

COMMENT ON TABLE test_suite_versions IS
	'storage_path points INSIDE storage.tests-directory, which is never served over HTTP, never copied into a template and never mounted writable.';

-- =====================================================================================
-- ASSIGNMENTS
-- =====================================================================================

CREATE TABLE assignments (
	id                   UUID        PRIMARY KEY,
	course_id            UUID        NOT NULL REFERENCES courses (id) ON DELETE RESTRICT,
	assignment_key       TEXT        NOT NULL,
	title                TEXT        NOT NULL,
	description          TEXT,
	display_order        INTEGER     NOT NULL DEFAULT 0,
	status               TEXT        NOT NULL DEFAULT 'DRAFT',
	mandatory            BOOLEAN     NOT NULL DEFAULT TRUE,
	opens_at             TIMESTAMPTZ,
	due_at               TIMESTAMPTZ,
	timezone             TEXT,
	max_points           NUMERIC(8,2) NOT NULL DEFAULT 100,
	test_count           INTEGER     NOT NULL DEFAULT 0,
	pass_threshold       NUMERIC(5,2) NOT NULL DEFAULT 100,
	allow_late           BOOLEAN     NOT NULL DEFAULT FALSE,
	template_version_id  UUID        REFERENCES template_versions (id) ON DELETE RESTRICT,
	test_suite_version_id UUID       REFERENCES test_suite_versions (id) ON DELETE RESTRICT,
	runtime_id           UUID        REFERENCES runtimes (id) ON DELETE RESTRICT,
	timeout_seconds      INTEGER,
	memory_limit_bytes   BIGINT,
	cpu_limit            NUMERIC(4,2),
	pid_limit            INTEGER,
	network_enabled      BOOLEAN     NOT NULL DEFAULT FALSE,
	created_at           TIMESTAMPTZ NOT NULL,
	updated_at           TIMESTAMPTZ NOT NULL,
	version              BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT assignments_key_unique UNIQUE (course_id, assignment_key),
	CONSTRAINT assignments_status_check
		CHECK (status IN ('DRAFT', 'SCHEDULED', 'OPEN', 'CLOSED', 'ARCHIVED')),
	CONSTRAINT assignments_schedule_order CHECK (opens_at IS NULL OR due_at IS NULL OR opens_at < due_at),
	CONSTRAINT assignments_threshold_range CHECK (pass_threshold >= 0 AND pass_threshold <= 100),
	CONSTRAINT assignments_points_positive  CHECK (max_points >= 0),
	CONSTRAINT assignments_key_format
		CHECK (assignment_key ~ '^[a-z0-9][a-z0-9._-]{0,62}[a-z0-9]$'),
	-- An assignment cannot leave DRAFT without everything needed to grade it
	-- reproducibly. Catching this in the database means a half configured assignment can
	-- never become reachable through an API bug.
	CONSTRAINT assignments_publishable CHECK (
		status = 'DRAFT' OR (
			template_version_id IS NOT NULL AND
			test_suite_version_id IS NOT NULL AND
			runtime_id IS NOT NULL AND
			opens_at IS NOT NULL AND
			due_at IS NOT NULL
		)
	)
);

CREATE INDEX assignments_course_idx ON assignments (course_id, display_order);
CREATE INDEX assignments_status_idx ON assignments (status);

CREATE TABLE deadline_extensions (
	id            UUID        PRIMARY KEY,
	assignment_id UUID        NOT NULL REFERENCES assignments (id) ON DELETE CASCADE,
	student_id    UUID        NOT NULL REFERENCES students (id) ON DELETE CASCADE,
	extended_due_at TIMESTAMPTZ NOT NULL,
	reason        TEXT        NOT NULL,
	granted_by    TEXT        NOT NULL,
	granted_at    TIMESTAMPTZ NOT NULL,
	revoked_at    TIMESTAMPTZ,
	revoked_by    TEXT,
	created_at    TIMESTAMPTZ NOT NULL,
	version       BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT deadline_extensions_reason_not_blank CHECK (length(btrim(reason)) > 0)
);

-- At most one live extension per student and assignment; superseded ones stay for audit.
CREATE UNIQUE INDEX deadline_extensions_active_key
	ON deadline_extensions (assignment_id, student_id) WHERE revoked_at IS NULL;

COMMENT ON TABLE deadline_extensions IS
	'Every extension records who granted it and why. Revocation is soft so the record survives.';

-- =====================================================================================
-- REPOSITORIES
-- =====================================================================================

CREATE TABLE repositories (
	id                  UUID        PRIMARY KEY,
	assignment_id       UUID        NOT NULL REFERENCES assignments (id) ON DELETE RESTRICT,
	student_id          UUID        NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
	repository_path     TEXT        NOT NULL,
	template_version_id UUID        REFERENCES template_versions (id) ON DELETE RESTRICT,
	provisioned_at      TIMESTAMPTZ,
	last_push_at        TIMESTAMPTZ,
	push_count          INTEGER     NOT NULL DEFAULT 0,
	size_bytes          BIGINT      NOT NULL DEFAULT 0,
	status              TEXT        NOT NULL DEFAULT 'PENDING',
	created_at          TIMESTAMPTZ NOT NULL,
	updated_at          TIMESTAMPTZ NOT NULL,
	version             BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT repositories_unique UNIQUE (assignment_id, student_id),
	CONSTRAINT repositories_path_unique UNIQUE (repository_path),
	CONSTRAINT repositories_status_check
		CHECK (status IN ('PENDING', 'READY', 'LOCKED', 'ARCHIVED'))
);

CREATE INDEX repositories_student_idx ON repositories (student_id);

COMMENT ON COLUMN repositories.template_version_id IS
	'The template version this repository was actually created from. Publishing a newer version leaves this untouched.';

-- =====================================================================================
-- SUBMISSIONS
-- =====================================================================================

CREATE TABLE submissions (
	id                    UUID         PRIMARY KEY,
	repository_id         UUID         NOT NULL REFERENCES repositories (id) ON DELETE RESTRICT,
	student_id            UUID         NOT NULL REFERENCES students (id) ON DELETE RESTRICT,
	course_id             UUID         NOT NULL REFERENCES courses (id) ON DELETE RESTRICT,
	assignment_id         UUID         NOT NULL REFERENCES assignments (id) ON DELETE RESTRICT,
	commit_sha            TEXT         NOT NULL,
	git_ref               TEXT         NOT NULL,
	commit_message        TEXT,
	commit_authored_at    TIMESTAMPTZ,
	received_at           TIMESTAMPTZ  NOT NULL,
	signature_status      TEXT         NOT NULL,
	signature_key_id      UUID         REFERENCES ssh_keys (id) ON DELETE RESTRICT,
	signature_fingerprint TEXT,
	transport_key_id      UUID         REFERENCES ssh_keys (id) ON DELETE RESTRICT,
	template_version_id   UUID         REFERENCES template_versions (id) ON DELETE RESTRICT,
	test_suite_version_id UUID         REFERENCES test_suite_versions (id) ON DELETE RESTRICT,
	runtime_id            UUID         REFERENCES runtimes (id) ON DELETE RESTRICT,
	runtime_image_digest  TEXT,
	status                TEXT         NOT NULL DEFAULT 'RECEIVED',
	late                  BOOLEAN      NOT NULL DEFAULT FALSE,
	effective_due_at      TIMESTAMPTZ,
	rejection_reason      TEXT,
	created_at            TIMESTAMPTZ  NOT NULL,
	CONSTRAINT submissions_status_check CHECK (status IN (
		'RECEIVED', 'QUEUED', 'RUNNING', 'PASSED', 'FAILED',
		'INFRASTRUCTURE_ERROR', 'CANCELLED', 'REJECTED')),
	CONSTRAINT submissions_signature_status_check
		CHECK (signature_status IN ('VERIFIED', 'UNSIGNED', 'INVALID', 'UNKNOWN_KEY', 'KEY_REVOKED', 'WRONG_OWNER')),
	CONSTRAINT submissions_commit_sha_format CHECK (commit_sha ~ '^[a-f0-9]{40}([a-f0-9]{24})?$'),
	CONSTRAINT submissions_unique_commit UNIQUE (repository_id, commit_sha, received_at)
);

CREATE INDEX submissions_student_idx     ON submissions (student_id, received_at DESC);
CREATE INDEX submissions_assignment_idx  ON submissions (assignment_id, received_at DESC);
CREATE INDEX submissions_course_idx      ON submissions (course_id, received_at DESC);
CREATE INDEX submissions_status_idx      ON submissions (status);
CREATE INDEX submissions_repository_idx  ON submissions (repository_id, received_at DESC);

COMMENT ON TABLE submissions IS
	'Append only. There is deliberately no updated_at and no optimistic locking version: a submission is a historical fact, and re-grading appends a grading_run instead of mutating this row.';
COMMENT ON COLUMN submissions.received_at IS
	'Server side receive time. This, not the client controlled commit date, decides lateness.';
COMMENT ON COLUMN submissions.signature_status IS
	'VERIFIED means: signed by a key registered to THIS student. It is not a claim about unaided authorship.';

-- =====================================================================================
-- GRADING
-- =====================================================================================

CREATE TABLE grading_runs (
	id                    UUID         PRIMARY KEY,
	submission_id         UUID         NOT NULL REFERENCES submissions (id) ON DELETE RESTRICT,
	attempt               INTEGER      NOT NULL DEFAULT 1,
	trigger               TEXT         NOT NULL DEFAULT 'PUSH',
	triggered_by          TEXT,
	status                TEXT         NOT NULL DEFAULT 'QUEUED',
	runtime_id            UUID         REFERENCES runtimes (id) ON DELETE RESTRICT,
	runtime_image_digest  TEXT,
	test_suite_version_id UUID         REFERENCES test_suite_versions (id) ON DELETE RESTRICT,
	grading_algorithm_version TEXT     NOT NULL DEFAULT 'v1',
	tests_total           INTEGER      NOT NULL DEFAULT 0,
	tests_passed          INTEGER      NOT NULL DEFAULT 0,
	tests_failed          INTEGER      NOT NULL DEFAULT 0,
	tests_errored         INTEGER      NOT NULL DEFAULT 0,
	tests_skipped         INTEGER      NOT NULL DEFAULT 0,
	score_percent         NUMERIC(6,3),
	points_awarded        NUMERIC(8,2),
	passed                BOOLEAN,
	exit_code             INTEGER,
	duration_ms           BIGINT,
	correlation_id        TEXT         NOT NULL,
	failure_category      TEXT,
	failure_detail        TEXT,
	started_at            TIMESTAMPTZ,
	finished_at           TIMESTAMPTZ,
	created_at            TIMESTAMPTZ  NOT NULL,
	CONSTRAINT grading_runs_status_check CHECK (status IN (
		'QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'TIMEOUT',
		'INFRASTRUCTURE_ERROR', 'CANCELLED')),
	CONSTRAINT grading_runs_trigger_check
		CHECK (trigger IN ('PUSH', 'MANUAL_RETRY', 'BULK_RETRY', 'SCHEDULED')),
	CONSTRAINT grading_runs_failure_category_check CHECK (failure_category IS NULL OR failure_category IN (
		'STUDENT_TEST_FAILURE', 'INVALID_SUBMISSION', 'RUNNER_ERROR',
		'INFRASTRUCTURE_ERROR', 'INTERNAL_ERROR')),
	CONSTRAINT grading_runs_counts_consistent
		CHECK (tests_passed + tests_failed + tests_errored + tests_skipped <= tests_total),
	CONSTRAINT grading_runs_score_range
		CHECK (score_percent IS NULL OR (score_percent >= 0 AND score_percent <= 100)),
	CONSTRAINT grading_runs_attempt_unique UNIQUE (submission_id, attempt)
);

CREATE INDEX grading_runs_submission_idx ON grading_runs (submission_id, attempt DESC);
CREATE INDEX grading_runs_status_idx     ON grading_runs (status);
CREATE INDEX grading_runs_correlation_idx ON grading_runs (correlation_id);

COMMENT ON TABLE grading_runs IS
	'Re-grading appends a run with attempt = max + 1. Earlier runs are never modified, which is what makes a disputed grade reconstructible.';
COMMENT ON COLUMN grading_runs.failure_category IS
	'Separates a student test failure from an infrastructure failure. An infrastructure failure must never be scored as a failed test.';

CREATE TABLE test_results (
	id             UUID         PRIMARY KEY,
	grading_run_id UUID         NOT NULL REFERENCES grading_runs (id) ON DELETE CASCADE,
	visibility     TEXT         NOT NULL,
	category       TEXT,
	test_name      TEXT,
	public_name    TEXT,
	outcome        TEXT         NOT NULL,
	weight         NUMERIC(6,3) NOT NULL DEFAULT 1,
	duration_ms    BIGINT,
	student_message TEXT,
	internal_message TEXT,
	display_order  INTEGER      NOT NULL DEFAULT 0,
	CONSTRAINT test_results_visibility_check CHECK (visibility IN ('PUBLIC', 'HIDDEN')),
	CONSTRAINT test_results_outcome_check CHECK (outcome IN (
		'PASSED', 'FAILED', 'TIMEOUT', 'INFRASTRUCTURE_ERROR', 'NOT_EXECUTED'))
);

CREATE INDEX test_results_run_idx ON test_results (grading_run_id, display_order);

COMMENT ON COLUMN test_results.test_name IS
	'Raw name from the report. For a HIDDEN test this is instructor-only and must never be serialised to a student facing response.';
COMMENT ON COLUMN test_results.public_name IS
	'Sanitised label safe to show a student. For a HIDDEN test this is a category, never the real test name.';
COMMENT ON COLUMN test_results.internal_message IS
	'Full assertion output including stack traces. Instructor-only for HIDDEN tests.';

CREATE TABLE grading_logs (
	id             UUID        PRIMARY KEY,
	grading_run_id UUID        NOT NULL REFERENCES grading_runs (id) ON DELETE CASCADE,
	stream         TEXT        NOT NULL,
	content        TEXT        NOT NULL,
	truncated      BOOLEAN     NOT NULL DEFAULT FALSE,
	byte_size      BIGINT      NOT NULL DEFAULT 0,
	created_at     TIMESTAMPTZ NOT NULL,
	CONSTRAINT grading_logs_stream_check CHECK (stream IN ('STDOUT', 'STDERR', 'RUNNER'))
);

CREATE INDEX grading_logs_run_idx ON grading_logs (grading_run_id);

COMMENT ON TABLE grading_logs IS
	'Instructor-only. Runner output can echo hidden test content, so it is never exposed on the public result page.';

-- =====================================================================================
-- JOB QUEUE
-- =====================================================================================

-- A database backed queue rather than an external broker. PostgreSQL SKIP LOCKED gives
-- exactly the semantics needed here and removes a whole service from the self-hosting
-- story; the interface stays narrow enough to slide a broker underneath later.
CREATE TABLE grading_jobs (
	id             UUID        PRIMARY KEY,
	grading_run_id UUID        NOT NULL REFERENCES grading_runs (id) ON DELETE CASCADE,
	submission_id  UUID        NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
	status         TEXT        NOT NULL DEFAULT 'PENDING',
	priority       INTEGER     NOT NULL DEFAULT 100,
	attempts       INTEGER     NOT NULL DEFAULT 0,
	max_attempts   INTEGER     NOT NULL DEFAULT 3,
	available_at   TIMESTAMPTZ NOT NULL,
	claimed_at     TIMESTAMPTZ,
	claimed_by     TEXT,
	claim_expires_at TIMESTAMPTZ,
	finished_at    TIMESTAMPTZ,
	last_error     TEXT,
	created_at     TIMESTAMPTZ NOT NULL,
	updated_at     TIMESTAMPTZ NOT NULL,
	version        BIGINT      NOT NULL DEFAULT 0,
	CONSTRAINT grading_jobs_status_check
		CHECK (status IN ('PENDING', 'CLAIMED', 'RUNNING', 'DONE', 'FAILED', 'CANCELLED')),
	CONSTRAINT grading_jobs_run_unique UNIQUE (grading_run_id)
);

-- Partial index: the dispatcher only ever scans runnable work, so the index stays small
-- no matter how much completed history accumulates.
CREATE INDEX grading_jobs_dispatch_idx
	ON grading_jobs (priority, available_at)
	WHERE status = 'PENDING';
CREATE INDEX grading_jobs_reaper_idx
	ON grading_jobs (claim_expires_at)
	WHERE status IN ('CLAIMED', 'RUNNING');

-- =====================================================================================
-- RESULT TOKENS
-- =====================================================================================

CREATE TABLE result_tokens (
	id            UUID         PRIMARY KEY,
	submission_id UUID         NOT NULL REFERENCES submissions (id) ON DELETE CASCADE,
	token_hash    TEXT         NOT NULL,
	token_prefix  TEXT         NOT NULL,
	issued_at     TIMESTAMPTZ  NOT NULL,
	expires_at    TIMESTAMPTZ,
	revoked_at    TIMESTAMPTZ,
	revoked_by    TEXT,
	last_access_at TIMESTAMPTZ,
	access_count  BIGINT       NOT NULL DEFAULT 0,
	CONSTRAINT result_tokens_hash_unique UNIQUE (token_hash)
);

CREATE INDEX result_tokens_submission_idx ON result_tokens (submission_id);

COMMENT ON TABLE result_tokens IS
	'Only the hash of a token is stored, so a database disclosure does not hand over readable result links.';
COMMENT ON COLUMN result_tokens.token_prefix IS
	'Leading characters only. Enough for support to correlate a report, not enough to open the page.';

-- =====================================================================================
-- AUDIT
-- =====================================================================================

CREATE TABLE audit_events (
	id            UUID        PRIMARY KEY,
	occurred_at   TIMESTAMPTZ NOT NULL,
	event_type    TEXT        NOT NULL,
	severity      TEXT        NOT NULL DEFAULT 'INFO',
	actor_type    TEXT        NOT NULL,
	actor_id      TEXT,
	actor_name    TEXT,
	subject_type  TEXT,
	subject_id    TEXT,
	course_id     UUID,
	outcome       TEXT        NOT NULL DEFAULT 'SUCCESS',
	source_ip_hash TEXT,
	correlation_id TEXT,
	detail        JSONB       NOT NULL DEFAULT '{}'::jsonb,
	CONSTRAINT audit_events_actor_type_check
		CHECK (actor_type IN ('STUDENT', 'INSTRUCTOR', 'ADMIN', 'SYSTEM', 'ANONYMOUS')),
	CONSTRAINT audit_events_outcome_check CHECK (outcome IN ('SUCCESS', 'FAILURE', 'DENIED')),
	CONSTRAINT audit_events_severity_check CHECK (severity IN ('INFO', 'NOTICE', 'WARNING', 'CRITICAL'))
);

CREATE INDEX audit_events_occurred_idx ON audit_events (occurred_at DESC);
CREATE INDEX audit_events_type_idx     ON audit_events (event_type, occurred_at DESC);
CREATE INDEX audit_events_subject_idx  ON audit_events (subject_type, subject_id);
CREATE INDEX audit_events_actor_idx    ON audit_events (actor_type, actor_id);
CREATE INDEX audit_events_detail_idx   ON audit_events USING gin (detail jsonb_path_ops);

COMMENT ON TABLE audit_events IS
	'Never contains private keys, passwords or complete result tokens. detail is structured so that a retention job can redact a single field.';

-- =====================================================================================
-- ABUSE CONTROL
-- =====================================================================================

CREATE TABLE registration_attempts (
	id           UUID        PRIMARY KEY,
	attempted_at TIMESTAMPTZ NOT NULL,
	ip_hash      TEXT        NOT NULL,
	outcome      TEXT        NOT NULL,
	reason       TEXT,
	student_number_hash TEXT,
	email_hash   TEXT,
	CONSTRAINT registration_attempts_outcome_check
		CHECK (outcome IN ('ACCEPTED', 'REJECTED', 'RATE_LIMITED', 'DUPLICATE'))
);

CREATE INDEX registration_attempts_ip_idx   ON registration_attempts (ip_hash, attempted_at DESC);
CREATE INDEX registration_attempts_time_idx ON registration_attempts (attempted_at DESC);

COMMENT ON TABLE registration_attempts IS
	'Addresses and identifiers are stored keyed-hashed only: enough to enforce a limit and investigate a flood, not a plaintext log of who tried to sign up.';

-- =====================================================================================
-- SYSTEM SETTINGS
-- =====================================================================================

CREATE TABLE system_settings (
	setting_key TEXT        PRIMARY KEY,
	value       TEXT,
	updated_at  TIMESTAMPTZ NOT NULL,
	updated_by  TEXT
);

COMMENT ON TABLE system_settings IS
	'Runtime-adjustable settings only. Anything set through the environment always wins, so a container restart cannot be overridden from the UI.';
