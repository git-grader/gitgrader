// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { fireEvent, screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { SubmissionGrading } from '../src/components/SubmissionGrading';
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
  expect(screen.getByText('alice')).toBeInTheDocument();
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

function gradingReport(submissionId = 's1') {
  return {
    courseId: 'c1', classId: 'cl1', studentId: 'st1', studentUsername: 'alice', fullName: 'Alice', status: 'SELF_REGISTERED',
    assignments: [{ assignmentId: 'a1', assignmentKey: 'strings', title: 'Strings', bestPercent: 0, bestPoints: 0,
      latestSubmission: { ...SUBMISSION, id: submissionId },
      latestGrading: { attempt: 2, status: 'COMPLETED', testsPassed: 0, testsTotal: 2, scorePercent: 0,
        pointsAwarded: 0, passed: false, finishedAt: null,
        tests: [
          { visibility: 'PUBLIC', publicName: 'Public check', outcome: 'FAILED', durationMs: 0, studentMessage: 'Try another input' },
          { visibility: 'HIDDEN', publicName: 'Private name', outcome: 'FAILED', studentMessage: 'Private details' }
        ]
      }
    }]
  };
}

test('shows zero scores and public test details without disclosing hidden details', async () => {
  server.use(http.get('/api/v1/reports/courses/c1/classes/cl1/students/st1', () => HttpResponse.json(gradingReport())));
  renderWithProviders(<SubmissionGrading submission={SUBMISSION} classId="cl1" />);
  expect(await screen.findByText('Score: 0%')).toBeInTheDocument();
  expect(screen.getByText('0 points')).toBeInTheDocument();
  expect(screen.getByText('0 of 2 tests passed')).toBeInTheDocument();
  expect(screen.getByText('Try another input')).toBeInTheDocument();
  expect(screen.getByText(/Hidden test/)).toBeInTheDocument();
  expect(screen.queryByText(/Private name|Private details/)).not.toBeInTheDocument();
});

test("never attributes a newer attempt's results to an older submission", async () => {
  server.use(http.get('/api/v1/reports/courses/c1/classes/cl1/students/st1', () => HttpResponse.json(gradingReport('newer'))));
  renderWithProviders(<SubmissionGrading submission={SUBMISSION} classId="cl1" />);
  expect(await screen.findByText(/not the latest attempt/)).toBeInTheDocument();
  expect(screen.queryByText('Score: 0%')).not.toBeInTheDocument();
});

test('distinguishes infrastructure failure from a graded zero', async () => {
  const report = gradingReport();
  server.use(http.get('/api/v1/reports/courses/c1/classes/cl1/students/st1', () => HttpResponse.json({
    ...report, assignments: report.assignments.map((assignment) => ({ ...assignment, latestGrading: null }))
  })));
  renderWithProviders(<SubmissionGrading submission={{ ...SUBMISSION, status: 'INFRASTRUCTURE_ERROR' }} classId="cl1" />);
  expect(await screen.findByText(/Grading could not finish/)).toBeInTheDocument();
  expect(screen.queryByText('Score: 0%')).not.toBeInTheDocument();
});
