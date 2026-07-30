-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- Spring Modulith event publication registry (schema version 2).
--
-- WHY THIS IS A MIGRATION AND NOT AUTO-CREATED
--   Spring Modulith can create these tables itself on startup. That is switched off
--   (spring.modulith.events.jdbc.schema-initialization.enabled=false) because the
--   application runs with hibernate ddl-auto=validate and Flyway as the single owner of
--   the schema. Two components creating tables is exactly how a production database
--   drifts away from what the migrations describe.
--
-- WHY IT MATTERS OPERATIONALLY
--   Cross-module events (SubmissionRecorded, GradingCompleted) are persisted here before
--   the listener runs. If the process dies between "push accepted" and "grading
--   finished", the publication is still incomplete on restart and is resubmitted. That
--   is what allows GitGrader to survive a restart mid-grading without an external broker.
--
-- Statements are taken verbatim from
-- org/springframework/modulith/events/jdbc/schemas/v2/schema-postgresql.sql so that an
-- upgrade of Spring Modulith can be diffed against this file.

CREATE TABLE IF NOT EXISTS event_publication (
	id                     UUID NOT NULL,
	listener_id            TEXT NOT NULL,
	event_type             TEXT NOT NULL,
	serialized_event       TEXT NOT NULL,
	publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
	completion_date        TIMESTAMP WITH TIME ZONE,
	status                 TEXT,
	completion_attempts    INT,
	last_resubmission_date TIMESTAMP WITH TIME ZONE,
	PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_serialized_event_hash_idx
	ON event_publication USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_by_completion_date_idx
	ON event_publication (completion_date);

CREATE TABLE IF NOT EXISTS event_publication_archive (
	id                     UUID NOT NULL,
	listener_id            TEXT NOT NULL,
	event_type             TEXT NOT NULL,
	serialized_event       TEXT NOT NULL,
	publication_date       TIMESTAMP WITH TIME ZONE NOT NULL,
	completion_date        TIMESTAMP WITH TIME ZONE,
	status                 TEXT,
	completion_attempts    INT,
	last_resubmission_date TIMESTAMP WITH TIME ZONE,
	PRIMARY KEY (id)
);

CREATE INDEX IF NOT EXISTS event_publication_archive_serialized_event_hash_idx
	ON event_publication_archive USING hash (serialized_event);
CREATE INDEX IF NOT EXISTS event_publication_archive_by_completion_date_idx
	ON event_publication_archive (completion_date);
