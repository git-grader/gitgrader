-- Copyright the GitGrader contributors.
-- SPDX-License-Identifier: Apache-2.0
--
-- Index the ordering the submissions list actually asks for.
--
-- The endpoint sorts by received_at DESC, id DESC and filters by course and status, but
-- every existing index stops at received_at. The tie-break on id is what makes the page
-- boundary stable, and without it in the index the first page of an instance with a term
-- of history behind it sorts rows it then throws away.

CREATE INDEX submissions_received_idx
	ON submissions (received_at DESC, id DESC);

CREATE INDEX submissions_course_status_received_idx
	ON submissions (course_id, status, received_at DESC, id DESC);
