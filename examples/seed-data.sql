-- Dates are relative to the moment this file is applied. They used to be fixed
-- calendar dates, which meant the worked example stopped being pushable once its
-- deadline passed and the first thing a new operator saw was a rejected push.
INSERT INTO courses (id, course_key, name, description, semester, starts_on, ends_on, timezone, status, registration_enabled, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000001', 'example-programming', 'Example Programming', 'A self-hosted demonstration course.', '2026-example', current_date - 30, current_date + 180, 'UTC', 'ACTIVE', TRUE, now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO course_classes (id, course_id, class_key, name, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'main', 'Main class', now(), now())
ON CONFLICT DO NOTHING;

INSERT INTO runtimes (id, runtime_key, display_name, image, tag, image_digest, install_command, test_command, report_format, enabled, created_at, updated_at)
VALUES ('10000000-0000-4000-8000-000000000003', 'node-24', 'Node.js 24', 'node', '24-bookworm-slim', 'sha256:3638d9a6fe4030bd716be989438248074489337ba3275657f93595428be4fc03', 'npm ci --ignore-scripts', 'node --test --test-reporter=tap /opt/hidden-tests/hidden.test.js', 'TAP', TRUE, now(), now())
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
  ('10000000-0000-4000-8000-000000000101', '10000000-0000-4000-8000-000000000001', 'assignment-01-string-utils', 'String utilities', 'Implement small text transformations.', 1, 'OPEN', TRUE, now() - interval '30 days', now() + interval '30 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000102', '10000000-0000-4000-8000-000000000001', 'assignment-02-arrays', 'Array transforms', 'Practice array transformations.', 2, 'SCHEDULED', TRUE, now() + interval '14 days', now() + interval '25 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000103', '10000000-0000-4000-8000-000000000001', 'assignment-03-objects', 'Object modelling', 'Model records with objects.', 3, 'SCHEDULED', TRUE, now() + interval '28 days', now() + interval '39 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000104', '10000000-0000-4000-8000-000000000001', 'assignment-04-files', 'File parsing', 'Parse structured text files.', 4, 'SCHEDULED', TRUE, now() + interval '42 days', now() + interval '53 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000105', '10000000-0000-4000-8000-000000000001', 'assignment-05-functions', 'Function composition', 'Compose small pure functions.', 5, 'SCHEDULED', TRUE, now() + interval '56 days', now() + interval '67 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000106', '10000000-0000-4000-8000-000000000001', 'assignment-06-modules', 'Module boundaries', 'Organise a small module graph.', 6, 'SCHEDULED', TRUE, now() + interval '70 days', now() + interval '81 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000107', '10000000-0000-4000-8000-000000000001', 'assignment-07-errors', 'Error handling', 'Handle expected failures clearly.', 7, 'SCHEDULED', TRUE, now() + interval '84 days', now() + interval '95 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000108', '10000000-0000-4000-8000-000000000001', 'assignment-08-async', 'Asynchronous work', 'Coordinate asynchronous operations.', 8, 'SCHEDULED', TRUE, now() + interval '98 days', now() + interval '109 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000109', '10000000-0000-4000-8000-000000000001', 'assignment-09-cli', 'Command-line interface', 'Build a command-line workflow.', 9, 'SCHEDULED', TRUE, now() + interval '112 days', now() + interval '123 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000110', '10000000-0000-4000-8000-000000000001', 'assignment-10-testing', 'Testing practice', 'Write focused automated checks.', 10, 'SCHEDULED', TRUE, now() + interval '126 days', now() + interval '137 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000111', '10000000-0000-4000-8000-000000000001', 'assignment-11-api-client', 'API client', 'Use a supplied local service.', 11, 'SCHEDULED', TRUE, now() + interval '140 days', now() + interval '151 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now()),
  ('10000000-0000-4000-8000-000000000112', '10000000-0000-4000-8000-000000000001', 'assignment-12-capstone', 'Small capstone', 'Combine the course techniques.', 12, 'SCHEDULED', TRUE, now() + interval '154 days', now() + interval '165 days', 'UTC', 100, 10, 70, FALSE, '10000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000007', '10000000-0000-4000-8000-000000000003', 30, 268435456, 1.00, 128, FALSE, now(), now())
ON CONFLICT DO NOTHING;
