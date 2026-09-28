// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import { SubmissionsPage } from '../src/pages/SubmissionsPage';
import { page, renderWithProviders, server } from './harness';

beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

const SUBMISSION = {
  id: 's1',
  repositoryId: 'r1',
  repositoryPath: 'students/alice/hw1',
  studentId: 'st1',
  studentUsername: 'alice',
  courseId: 'c1',
  assignmentId: 'a1',
  commitSha: 'f0e1d2c3b4a5968778998aa9bbccddeeff001122',
  shortCommitSha: 'f0e1d2c',
  gitRef: 'refs/heads/main',
  commitMessage: 'Implement the report endpoint',
  receivedAt: '2026-03-01T10:00:00Z',
  signatureStatus: 'VERIFIED',
  signatureFingerprint: 'SHA256:abc123',
  status: 'PASSED',
  late: true,
  effectiveDueAt: '2026-02-28T23:59:59Z',
  rejectionReason: null,
  runtimeImageDigest: 'sha256:abcd'
};

test('shows the student\'s username in the submissions table', async () => {
  server.use(
    http.get('/api/v1/courses', () => HttpResponse.json(page([]))),
    http.get('/api/v1/submissions', () => HttpResponse.json(page([SUBMISSION])))
  );

  renderWithProviders(<SubmissionsPage />);

  expect(await screen.findByRole('columnheader', { name: 'Student' })).toBeInTheDocument();
  expect(await screen.findByText('alice')).toBeInTheDocument();
});