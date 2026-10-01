// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { StudentDetailPage } from '../src/pages/StudentDetailPage';
import { renderWithProviders, server } from './harness';

beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

const STUDENT = {
  id: 's1',
  studentUsername: 'alice',
  firstName: 'Alice',
  lastName: 'Lee',
  email: 'alice@example.org',
  status: 'ACTIVE'
};

function renderDetail(
  routes = [<Route key="d" path="/students/:id" element={<StudentDetailPage />} />],
  route = '/students/s1'
) {
  renderWithProviders(<Routes>{routes}</Routes>, { route });
}

function stubStudent() {
  server.use(http.get('/api/v1/students/s1', () => HttpResponse.json({ student: STUDENT, sshKeys: [] })));
}

test('edits the loaded student', async () => {
  stubStudent();
  renderDetail();

  expect(await screen.findByRole('heading', { name: 'Edit Student' })).toBeInTheDocument();
  expect(screen.getByRole('textbox', { name: 'Student ID / Username' })).toHaveValue('alice');
  expect(screen.getByRole('textbox', { name: 'First Name' })).toHaveValue('Alice');
  expect(screen.getByRole('textbox', { name: 'Last Name' })).toHaveValue('Lee');
  expect(screen.getByRole('textbox', { name: 'Email' })).toHaveValue('alice@example.org');
  expect(screen.getByRole('region', { name: 'Student status' })).toBeInTheDocument();
});

test('saves the changes and returns to the list', async () => {
  stubStudent();
  let body: unknown;
  server.use(http.put('/api/v1/students/s1', async ({ request }) => {
    body = await request.json();
    return HttpResponse.json({ ...STUDENT, firstName: 'Alicia' });
  }));
  renderDetail([<Route key="l" path="/students" element={<div>Students list</div>} />,
    <Route key="d" path="/students/:id" element={<StudentDetailPage />} />]);

  await screen.findByRole('heading', { name: 'Edit Student' });
  fireEvent.change(screen.getByRole('textbox', { name: 'First Name' }), { target: { value: 'Alicia' } });
  await userEvent.click(screen.getByRole('button', { name: 'Save' }));

  await waitFor(() => { expect(body).toMatchObject({ firstName: 'Alicia', studentUsername: 'alice', email: 'alice@example.org' }); });
  expect(await screen.findByText('Students list')).toBeInTheDocument();
});

test('shows the field the server rejected the update on', async () => {
  stubStudent();
  server.use(http.put('/api/v1/students/s1', () => new HttpResponse(
    JSON.stringify({
      type: 'about:blank',
      title: 'Unprocessable Entity',
      status: 422,
      detail: 'The update was rejected.',
      errors: [{ field: 'studentUsername', message: 'That student username is already registered' }]
    }),
    { status: 422, headers: { 'content-type': 'application/problem+json' } }
  )));
  renderDetail();

  await screen.findByRole('heading', { name: 'Edit Student' });
  await userEvent.click(screen.getByRole('button', { name: 'Save' }));

  expect(await screen.findByText('The update was rejected.')).toBeInTheDocument();
  expect(screen.getByText('That student username is already registered')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Save' })).toBeEnabled();
});

test('shows read-only latest coursework while preserving best score and hiding hidden-test internals', async () => {
  stubStudent();
  server.use(http.get('/api/v1/reports/courses/course-1/classes/class-1/students/s1', () => HttpResponse.json({
    courseId: 'course-1', classId: 'class-1', studentId: 's1', studentUsername: 'alice',
    fullName: 'Alice Lee', status: 'VERIFIED_BY_INSTRUCTOR',
    assignments: [{
      assignmentId: 'a1', assignmentKey: 'strings', title: 'String utilities', bestPercent: 90, bestPoints: 9,
      latestSubmission: {
        id: 'submission-1', commitSha: 'abc123', gitRef: 'refs/heads/main', commitMessage: 'Latest attempt',
        receivedAt: '2026-09-20T12:00:00Z', status: 'FAILED', late: true
      },
      latestGrading: {
        attempt: 2, status: 'COMPLETED', testsPassed: 2, testsTotal: 4, scorePercent: 45,
        pointsAwarded: 4.5, passed: false, finishedAt: '2026-09-20T12:01:00Z',
        tests: [
          { visibility: 'PUBLIC', category: 'correctness', publicName: 'Handles empty input', outcome: 'PASSED', durationMs: 8, studentMessage: 'Passed' },
          { visibility: 'HIDDEN', category: 'security', publicName: null, outcome: 'FAILED', durationMs: 11, studentMessage: null }
        ]
      }
    }]
  })));
  renderDetail(undefined, '/students/s1?courseId=course-1&classId=class-1');

  expect(await screen.findByRole('heading', { name: 'Coursework' })).toBeInTheDocument();
  expect(screen.getByText(/Best result: 90%/)).toBeInTheDocument();
  expect(screen.getByText(/Latest attempt: 45%/)).toBeInTheDocument();
  expect(screen.getByText(/2 of 4 tests passed/)).toBeInTheDocument();
  expect(screen.getByText(/Hidden test · security · FAILED · 11 ms/)).toBeInTheDocument();
  expect(screen.getByText('Latest attempt')).toBeInTheDocument();
  expect(screen.getByText('Late')).toBeInTheDocument();
  expect(screen.getByRole('link', { name: 'Back to class' })).toHaveAttribute('href', '/courses/course-1/classes/class-1');
  expect(document.body.textContent).not.toMatch(/secret assertion|secret hint|internal message/i);
  expect(screen.queryByRole('textbox', { name: /grade/i })).not.toBeInTheDocument();
});

test('distinguishes ungraded and infrastructure-error attempts from failed student tests', async () => {
  stubStudent();
  server.use(http.get('/api/v1/reports/courses/course-1/classes/class-1/students/s1', () => HttpResponse.json({
    courseId: 'course-1', classId: 'class-1', studentId: 's1', studentUsername: 'alice',
    fullName: 'Alice Lee', status: 'VERIFIED_BY_INSTRUCTOR',
    assignments: [
      {
        assignmentId: 'a1', assignmentKey: 'ungraded', title: 'Not graded', bestPercent: 0, bestPoints: 0,
        latestSubmission: {
          id: 'submission-1', commitSha: 'abc123', gitRef: 'refs/heads/main', commitMessage: null,
          receivedAt: '2026-09-20T12:00:00Z', status: 'RECEIVED', late: false
        }, latestGrading: null
      },
      {
        assignmentId: 'a2', assignmentKey: 'infra', title: 'Runner unavailable', bestPercent: 0, bestPoints: 0,
        latestSubmission: {
          id: 'submission-2', commitSha: 'def456', gitRef: 'refs/heads/main', commitMessage: null,
          receivedAt: '2026-09-20T13:00:00Z', status: 'INFRASTRUCTURE_ERROR', late: false
        }, latestGrading: null
      }
    ]
  })));
  renderDetail(undefined, '/students/s1?courseId=course-1&classId=class-1');

  expect(await screen.findByText('Not graded yet')).toBeInTheDocument();
  expect(screen.getByText('Infrastructure error; no student test results are available.')).toBeInTheDocument();
  expect(screen.queryByText(/0 of 0 tests passed/)).not.toBeInTheDocument();
});