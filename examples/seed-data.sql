INSERT INTO courses (id, course_key, name, description, semester, starts_on, ends_on, timezone, status, registration_enabled, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000001', 'example-programming', 'Example Programming', 'A self-hosted demonstration course.', '2026-example', DATE '2026-01-12', DATE '2026-05-15', 'UTC', 'ACTIVE', TRUE, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO course_classes (id, course_id, class_key, name, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'main', 'Main class', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest, install_command, test_command, report_format, enabled, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000003', 'node-24', 'Node.js 24', 'node', '24-bookworm-slim', 'sha256:6f7b03f7c2c8e2e784dcf9295400527b9b1270fd37b7e9a7285cf83b6951452d', 'npm ci --ignore-scripts', 'node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js', 'TAP', TRUE, now(), now())
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
  ('10000000-0000-4000-8000-000000000101', '10000000-0000-4000-8000-000000000001', 'assignment-01-string-utils', 'String utilities', 'Implement small text transformations.', 1, 'OPEN', TRUE, TIMESTAMPTZ '2026-01-12 09:00:00+00', TIMESTAMPTZ '2026-01-23 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000102', '10000000-0000-4000-8000-000000000001', 'assignment-02-arrays', 'Array transforms', 'Practice array transformations.', 2, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-01-26 09:00:00+00', TIMESTAMPTZ '2026-02-06 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000103', '10000000-0000-4000-8000-000000000001', 'assignment-03-objects', 'Object modelling', 'Model records with objects.', 3, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-02-09 09:00:00+00', TIMESTAMPTZ '2026-02-20 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000104', '10000000-0000-4000-8000-000000000001', 'assignment-04-files', 'File parsing', 'Parse structured text files.', 4, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-02-23 09:00:00+00', TIMESTAMPTZ '2026-03-06 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000105', '10000000-0000-4000-8000-000000000001', 'assignment-05-functions', 'Function composition', 'Compose small pure functions.', 5, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-03-09 09:00:00+00', TIMESTAMPTZ '2026-03-20 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000106', '10000000-0000-4000-8000-000000000001', 'assignment-06-modules', 'Module boundaries', 'Organise a small module graph.', 6, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-03-23 09:00:00+00', TIMESTAMPTZ '2026-04-03 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000107', '10000000-0000-4000-8000-000000000001', 'assignment-07-errors', 'Error handling', 'Handle expected failures clearly.', 7, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-04-06 09:00:00+00', TIMESTAMPTZ '2026-04-17 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000108', '10000000-0000-4000-8000-000000000001', 'assignment-08-async', 'Asynchronous work', 'Coordinate asynchronous operations.', 8, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-04-20 09:00:00+00', TIMESTAMPTZ '2026-05-01 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000109', '10000000-0000-4000-8000-000000000001', 'assignment-09-cli', 'Command-line interface', 'Build a command-line workflow.', 9, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-05-04 09:00:00+00', TIMESTAMPTZ '2026-05-15 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000110', '10000000-0000-4000-8000-000000000001', 'assignment-10-testing', 'Testing practice', 'Write focused automated checks.', 10, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-05-18 09:00:00+00', TIMESTAMPTZ '2026-05-29 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000111', '10000000-0000-4000-8000-000000000001', 'assignment-11-api-client', 'API client', 'Use a supplied local service.', 11, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-06-01 09:00:00+00', TIMESTAMPTZ '2026-06-12 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000112', '10000000-0000-4000-8000-000000000001', 'assignment-12-capstone', 'Small capstone', 'Combine the course techniques.', 12, 'SCHEDULED', TRUE, TIMESTAMPTZ '2026-06-15 09:00:00+00', TIMESTAMPTZ '2026-06-26 23:59:00+00', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now())
ON CONFLICT DO NOTHING;
