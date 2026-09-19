// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { fireEvent, screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { SubmissionDetailPage } from '../src/pages/SubmissionDetailPage';
import { renderWithProviders, server } from './harness';

beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

const SUBMISSION = {
  id: 's1',
  repositoryId: 'r1',
  repositoryPath: 'students/alice/hw1',
  studentId: 'st1',
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

function renderDetail(routes = [<Route key="d" path="/submissions/:id" element={<SubmissionDetailPage />} />]) {
  renderWithProviders(<Routes>{routes}</Routes>, { route: '/submissions/s1' });
}

test('shows the submission the server returned', async () => {
  server.use(http.get('/api/v1/submissions/s1', () => HttpResponse.json(SUBMISSION)));
  renderDetail();

  expect(await screen.findByRole('heading', { name: 'Submission' })).toBeInTheDocument();
  expect(screen.getByText('Passed')).toBeInTheDocument();
  expect(screen.getByText('Late')).toBeInTheDocument();
  expect(screen.getByText('VERIFIED')).toBeInTheDocument();
  expect(screen.getByText(SUBMISSION.commitSha)).toBeInTheDocument();
  expect(screen.getByText(SUBMISSION.gitRef)).toBeInTheDocument();
  expect(screen.getByText(SUBMISSION.commitMessage)).toBeInTheDocument();
  expect(screen.getByText(SUBMISSION.signatureFingerprint)).toBeInTheDocument();
  expect(screen.getByText(SUBMISSION.runtimeImageDigest)).toBeInTheDocument();
  // Nullish fields render as a dash rather than nothing, so a missing rejection reason
  // and an empty box do not look alike.
  expect(screen.getAllByText('—').length).toBeGreaterThan(0);
});

test('leaves the detail page for the list', async () => {
  server.use(http.get('/api/v1/submissions/s1', () => HttpResponse.json(SUBMISSION)));
  renderDetail([<Route key="l" path="/submissions" element={<div>Submission list</div>} />,
    <Route key="d" path="/submissions/:id" element={<SubmissionDetailPage />} />]);

  await screen.findByRole('heading', { name: 'Submission' });
  fireEvent.click(screen.getByRole('button', { name: 'Back to submissions' }));

  expect(await screen.findByText('Submission list')).toBeInTheDocument();
});

test('reports a failed load and can be retried', async () => {
  server.use(http.get('/api/v1/submissions/s1', () => new HttpResponse(null, { status: 500 })));
  renderDetail();

  expect(await screen.findByText('The submission could not be loaded.')).toBeInTheDocument();

  server.use(http.get('/api/v1/submissions/s1', () => HttpResponse.json(SUBMISSION)));
  fireEvent.click(screen.getByRole('button', { name: 'Retry' }));

  expect(await screen.findByRole('heading', { name: 'Submission' })).toBeInTheDocument();
});