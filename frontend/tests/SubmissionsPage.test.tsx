// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import userEvent from '@testing-library/user-event';
import { screen, waitFor } from '@testing-library/react';
import { queryKeys } from '../src/api/queryKeys';
import { SubmissionsPage } from '../src/pages/SubmissionsPage';
import { createTestQueryClient, page, renderWithProviders, server } from './harness';


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

it('shows the student\'s username in the submissions table', async () => {
  server.use(
    http.get('/api/v1/courses', () => HttpResponse.json(page([]))),
    http.get('/api/v1/submissions', () => HttpResponse.json(page([SUBMISSION])))
  );

  renderWithProviders(<SubmissionsPage />);

  expect(await screen.findByRole('columnheader', { name: 'Student' })).toBeInTheDocument();
  expect(await screen.findByText('alice')).toBeInTheDocument();
});

it('reuses course choices loaded by Assignments without breaking the page', async () => {
  const queryClient = createTestQueryClient();
  queryClient.setQueryData(queryKeys.courses.choices, [{
    id: 'c1', courseKey: 'cs101', name: 'Course One', description: null, semester: null,
    startsOn: null, endsOn: null, timezone: 'Europe/Zurich', status: 'ACTIVE',
    registrationOpensAt: null, registrationClosesAt: null, registrationEnabled: true
  }]);
  server.use(
    http.get('/api/v1/courses', () => HttpResponse.json(page([]))),
    http.get('/api/v1/submissions', () => HttpResponse.json(page([SUBMISSION])))
  );

  renderWithProviders(<SubmissionsPage />, { queryClient });

  expect(await screen.findByText('alice')).toBeInTheDocument();
  expect(screen.getByRole('combobox', { name: 'Course Filter' })).toBeInTheDocument();
});


it('passes scoped filters to the REST API and retries only after confirmation', async () => {
  let requested: URL | undefined;
  let retries = 0;
  server.use(
    http.get('/api/v1/courses', () => HttpResponse.json(page([]))),
    http.get('/api/v1/submissions', ({ request }) => {
      requested = new URL(request.url);
      return HttpResponse.json(page([{ ...SUBMISSION, status: 'INFRASTRUCTURE_ERROR' }]));
    }),
    http.post('/api/v1/submissions/s1/regrade', () => {
      retries++;
      return HttpResponse.json({ gradingRunId: 'run1' }, { status: 202 });
    })
  );
  renderWithProviders(<SubmissionsPage />, { route: '/submissions?status=INFRASTRUCTURE_ERROR&studentId=st1&assignmentId=a1' });
  const user = userEvent.setup();
  await screen.findByText(/1 submission has infrastructure errors in this selection/);
  expect(requested?.searchParams.get('studentId')).toBe('st1');
  expect(requested?.searchParams.get('assignmentId')).toBe('a1');
  expect(requested?.searchParams.get('status')).toBe('INFRASTRUCTURE_ERROR');
  await user.click(await screen.findByRole('button', { name: 'Retry grading' }));
  expect(retries).toBe(0);
  await user.click(screen.getByRole('button', { name: 'Cancel' }));
  expect(retries).toBe(0);
  await user.click(await screen.findByRole('button', { name: 'Retry grading' }));
  await user.click(screen.getByRole('button', { name: 'Queue retry' }));
  await screen.findByText('Grading retry queued.');
  expect(retries).toBe(1);
});

it('keeps a failed retry visible and allows another try', async () => {
  server.use(
    http.get('/api/v1/courses', () => HttpResponse.json(page([]))),
    http.get('/api/v1/submissions', () => HttpResponse.json(page([{ ...SUBMISSION, status: 'INFRASTRUCTURE_ERROR' }]))),
    http.post('/api/v1/submissions/s1/regrade', () => HttpResponse.json({ status: 500, title: 'Retry failed', detail: 'Queue unavailable' }, { status: 500, headers: { 'content-type': 'application/problem+json' } }))
  );
  renderWithProviders(<SubmissionsPage />, { route: '/submissions?status=INFRASTRUCTURE_ERROR' });
  const user = userEvent.setup();
  await user.click(await screen.findByRole('button', { name: 'Retry grading' }));
  await user.click(screen.getByRole('button', { name: 'Queue retry' }));
  expect(await screen.findByText('Queue unavailable')).toBeInTheDocument();
  expect(screen.getByRole('dialog')).toBeInTheDocument();
  server.use(http.post('/api/v1/submissions/s1/regrade', () => HttpResponse.json({ gradingRunId: 'run1' }, { status: 202 })));
  await user.click(screen.getByRole('button', { name: 'Queue retry' }));
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  expect(screen.getByText('Grading retry queued.')).toBeInTheDocument();
});
