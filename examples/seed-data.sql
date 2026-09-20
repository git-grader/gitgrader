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
VALUES ('10000000-0000-4000-8000-000000000003', 'node-24', 'Node.js 24', 'node', '24-bookworm-slim', 'sha256:2fe369e969550cde8e867afc3fe370b260140cab4a23d467074295b42163d553', NULL, 'cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs', 'TAP', TRUE, 'node-ipc', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest, install_command, test_command, report_format, enabled, shim_kind, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000013', 'node-22', 'Node.js 22', 'node', '22-bookworm-slim', 'sha256:48e4b67d85f87bd551df43704e24d252f56cc5f8e9718841aace50f19948f0f9', NULL, 'cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs', 'TAP', TRUE, 'node-ipc', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest, install_command, test_command, report_format, enabled, shim_kind, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000014', 'node-26', 'Node.js 26', 'node', '26-bookworm-slim', 'sha256:582460f614631b59b824ac6020533b9bf339c7fdf3a6d7db31abb6b4065f0212', NULL, 'cd /opt/hidden-tests && jasmine --config=jasmine.json --reporter=./jasmine-tap-reporter.cjs', 'TAP', TRUE, 'node-ipc', now(), now())
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
