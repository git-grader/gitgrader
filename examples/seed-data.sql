-- Dates are relative to the moment this file is applied. They used to be fixed
-- calendar dates, which meant the worked example stopped being pushable once its
-- deadline passed and the first thing a new operator saw was a rejected push.
INSERT INTO courses (id, course_key, name, description, semester, starts_on, ends_on, timezone, status, registration_enabled, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000001', 'example-programming', 'Example Programming', 'A self-hosted demonstration course.', '2026-example', current_date - 30, current_date + 180, 'UTC', 'ACTIVE', TRUE, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO course_classes (id, course_id, class_key, name, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'main', 'Main class', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest, install_command, test_command, report_format, enabled, shim_kind, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000003', 'node-24', 'Node.js 24', 'node', '24-bookworm-slim', 'sha256:55fb5f5a527ea2c5c4a95f963e48ef90aa38c7b346054fdc8ee9957a2b55d205', NULL, 'cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs', 'TAP', TRUE, 'node-ipc', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest, install_command, test_command, report_format, enabled, shim_kind, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000013', 'node-22', 'Node.js 22', 'node', '22-bookworm-slim', 'sha256:4b150b48b4116e2a22f2c39cc27025fbf4f153e2078fd3b14d159fdb5c7279da', NULL, 'cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs', 'TAP', TRUE, 'node-ipc', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest, install_command, test_command, report_format, enabled, shim_kind, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000014', 'node-26', 'Node.js 26', 'node', '26-bookworm-slim', 'sha256:28147155d47dc31abe0a46e0d12b59b2fb3d0a2dcc5dc65fce76a074180b16fe', NULL, 'cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs', 'TAP', TRUE, 'node-ipc', now(), now())
ON CONFLICT DO NOTHING;

-- The Python shim is started explicitly rather than through the runner's Node
-- default command, so its runtime row names the interpreter that starts it. The
-- suite half reports TAP through the baked tap_runner.py (pytest-tap's
-- --tap-stream omits the "- " separator the grading TAP parser requires).
INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest, install_command, test_command, report_format, enabled, shim_kind, shim_command, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000015', 'python-3.14', 'Python 3.14', 'python', '3.14-slim-bookworm', 'sha256:96599df822d0e1c6bb700014a05c7f3c8cabc6e0f7ff3c4f9f69cee033b4b5d9', NULL, 'cd /opt/hidden-tests && python3 /opt/gitgrader-shim/tap_runner.py', 'TAP', TRUE, 'python-ipc', 'python3 /opt/gitgrader-shim/server.py', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO project_templates (id, template_key, name, description, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000004', 'assignment-01-string-utils', 'String utilities template', 'Student-visible starter project.', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO template_versions (id, template_id, version_label, storage_path, content_hash, file_count, total_bytes, published_at, published_by, created_at)
VALUES ('10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000004', '1.0.0', 'examples/assignments/assignment-01-string-utils/template', 'f81cfb4e58d7b50217bf53e10db3f2dc6316bcbb399dd99bb908e7bf78996a22', 9, 2762, now(), 'system', now())
ON CONFLICT DO NOTHING;

INSERT INTO test_suites (id, suite_key, name, description, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000006', 'assignment-01-string-utils', 'String utilities checks', 'Operator-only checks for the string utilities sample.', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO test_suite_versions (id, suite_id, version_label, storage_path, content_hash, hidden_test_count, public_test_count, published_at, published_by, created_at)
VALUES ('10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000006', '1.0.0', 'examples/assignments/assignment-01-string-utils/hidden-tests', '23d6faf790d426ea1f943ca321835873068b2f14def532466c9b12f9d6cf39b2', 10, 6, now(), 'system', now())
ON CONFLICT DO NOTHING;

INSERT INTO assignments (id, course_id, assignment_key, title, description, display_order, status, mandatory, opens_at, due_at, timezone, max_points, test_count, pass_threshold, allow_late, template_version_id, test_suite_version_id, runtime_id, timeout_seconds, memory_limit_bytes, cpu_limit, pid_limit, network_enabled, created_at, updated_at)
VALUES
  ('10000000-0000-4000-8000-000000000101', '10000000-0000-4000-8000-000000000001', 'assignment-01-string-utils', 'String utilities', 'Implement small text transformations.', 1, 'OPEN', TRUE, now() - interval '30 days', now() + interval '30 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now())
ON CONFLICT DO NOTHING;

-- A sandbox container runs with no network, so the shimmed node runtimes have
-- nothing to install at grading time (Jasmine is baked into the image). The
-- update below also repairs databases that applied an older seed with
-- `npm ci --ignore-scripts`.
UPDATE runtimes SET install_command = NULL, updated_at = now()
WHERE runtime_key IN ('node-22', 'node-24', 'node-26');

-- The Java and Python string-utils examples mirror the Node one: same ten
-- hidden checks, complete and partial-70 reference solutions. Content hashes
-- are the SHA-256 metadata ContentHasher computes over the sorted relative
-- paths and file bytes of the stored directory.
INSERT INTO project_templates (id, template_key, name, description, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000008', 'assignment-02-string-utils-java', 'String utilities template (Java)', 'Student-visible starter project for the Java string utilities.', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO template_versions (id, template_id, version_label, storage_path, content_hash, file_count, total_bytes, published_at, published_by, created_at)
VALUES ('10000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000008', '1.0.0', 'examples/assignments/assignment-02-string-utils-java/template', 'f359af5fb1abddddeed996d877f5c5b61b4480de970937d3cc5aebf9edcdf913', 4, 2438, now(), 'system', now())
ON CONFLICT DO NOTHING;

INSERT INTO test_suites (id, suite_key, name, description, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000012', 'assignment-02-string-utils-java', 'String utilities checks (Java)', 'Operator-only checks for the Java string utilities sample.', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO test_suite_versions (id, suite_id, version_label, storage_path, content_hash, hidden_test_count, public_test_count, published_at, published_by, created_at)
VALUES ('10000000-0000-4000-8000-000000000017', '10000000-0000-4000-8000-000000000012', '1.0.0', 'examples/assignments/assignment-02-string-utils-java/hidden-tests', 'c58ab11309ba7b30a3d37dd976b06128f35c1991a9d0bca6062f795fa8137f03', 10, 3, now(), 'system', now())
ON CONFLICT DO NOTHING;

INSERT INTO project_templates (id, template_key, name, description, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000009', 'assignment-03-string-utils-python', 'String utilities template (Python)', 'Student-visible starter project for the Python string utilities.', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO template_versions (id, template_id, version_label, storage_path, content_hash, file_count, total_bytes, published_at, published_by, created_at)
VALUES ('10000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000009', '1.0.0', 'examples/assignments/assignment-03-string-utils-python/template', 'fb3f82f97eb7fc7c2d09c3c22f98dc97deba64f0bf078bb31eaf18ee8d2672aa', 4, 2188, now(), 'system', now())
ON CONFLICT DO NOTHING;

INSERT INTO test_suites (id, suite_key, name, description, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000016', 'assignment-03-string-utils-python', 'String utilities checks (Python)', 'Operator-only checks for the Python string utilities sample.', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO test_suite_versions (id, suite_id, version_label, storage_path, content_hash, hidden_test_count, public_test_count, published_at, published_by, created_at)
VALUES ('10000000-0000-4000-8000-000000000018', '10000000-0000-4000-8000-000000000016', '1.0.0', 'examples/assignments/assignment-03-string-utils-python/hidden-tests', '3271becd5518907c96ef78bf0367b082c8ac9ef96cbd3939f517d20d0c9c89d4', 10, 3, now(), 'system', now())
ON CONFLICT DO NOTHING;

INSERT INTO assignments (id, course_id, assignment_key, title, description, display_order, status, mandatory, opens_at, due_at, timezone, max_points, test_count, pass_threshold, allow_late, template_version_id, test_suite_version_id, runtime_id, timeout_seconds, memory_limit_bytes, cpu_limit, pid_limit, network_enabled, created_at, updated_at)
VALUES
  ('10000000-0000-4000-8000-000000000103', '10000000-0000-4000-8000-000000000001', 'assignment-03-string-utils-python', 'String utilities (Python)', 'Implement small text transformations in Python.', 3, 'OPEN', TRUE, now() - interval '30 days', now() + interval '30 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000011', '10000000-0000-4000-8000-000000000018', '10000000-0000-4000-8000-000000000015', 30, 268435456, 1.00, 128, FALSE, now(), now())
ON CONFLICT DO NOTHING;

-- The Java assignment row is intentionally not seeded: assignments.runtime_id is
-- NOT NULL and the java-25/java-27 runtime rows are registered manually once the
-- built runtime image's registry digest is known (see
-- deployment/runtimes/java-25/README.md). After registering a Java runtime, add:
--   INSERT INTO assignments (id, course_id, assignment_key, title, description, display_order, status, mandatory, opens_at, due_at, timezone, max_points, test_count, pass_threshold, allow_late, template_version_id, test_suite_version_id, runtime_id, timeout_seconds, memory_limit_bytes, cpu_limit, pid_limit, network_enabled, created_at, updated_at)
--   VALUES
--     ('10000000-0000-4000-8000-000000000102', '10000000-0000-4000-8000-000000000001', 'assignment-02-string-utils-java', 'String utilities (Java)', 'Implement small text transformations in Java.', 2, 'OPEN', TRUE, now() - interval '30 days', now() + interval '30 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000010', '10000000-0000-4000-8000-000000000017', '<java runtime id>', 30, 268435456, 1.00, 128, FALSE, now(), now());
