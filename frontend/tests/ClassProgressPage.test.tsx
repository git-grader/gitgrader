// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { ClassProgressPage } from '../src/pages/ClassProgressPage';
import { renderWithProviders, server } from './harness';

const CLASS_REPORT = {
  courseId: 'course-1', classId: 'class-1', classKey: 'a', className: 'Class A',
  totalMandatoryAssignments: 1, totalPointsAvailable: 10,
  assignments: [{
    assignmentId: 'assignment-1', assignmentKey: 'strings', title: 'String utilities', mandatory: true,
    maxPoints: 10, testCount: 4, submissionCount: 1, passedCount: 0, failedCount: 1,
    infrastructureErrorCount: 0, notStartedCount: 1, averagePercent: 45
  }],
  students: [
    {
      studentId: 'student-1', studentUsername: 'a001', fullName: 'Ada Lovelace', status: 'VERIFIED_BY_INSTRUCTOR',
      enrollmentStatus: 'ACTIVE', fullyCompleted: 0, partiallyCompleted: 1, notStarted: 0,
      completionRate: 0, pointsEarned: 4.5, pointsRate: 0.45, totalPoints: 10, submissionCount: 1,
      lastActivityAt: '2026-09-20T12:00:00Z', assignments: {
        strings: {
          bestPercent: 45, bestPoints: 4.5,
          latestSubmission: {
            id: 'submission-1', commitSha: 'abc123', gitRef: 'refs/heads/main', commitMessage: 'First pass',
            receivedAt: '2026-09-20T12:00:00Z', status: 'FAILED', late: false
          },
          latestGrading: {
            submissionId: 'submission-1', attempt: 1, status: 'COMPLETED', testsPassed: 2, testsTotal: 4,
            scorePercent: 45, pointsAwarded: 4.5, passed: false, finishedAt: '2026-09-20T12:01:00Z'
          }
        }
      }
    },
    {
      studentId: 'student-2', studentUsername: 'g002', fullName: 'Grace Hopper', status: 'SELF_REGISTERED',
      enrollmentStatus: 'WITHDRAWN', fullyCompleted: 0, partiallyCompleted: 0, notStarted: 1,
      completionRate: 0, pointsEarned: 0, pointsRate: 0, totalPoints: 10, submissionCount: 0,
      lastActivityAt: null, assignments: {
        strings: { bestPercent: 0, bestPoints: 0, latestSubmission: null, latestGrading: null }
      }
    }
  ]
};

beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
afterEach(() => { server.resetHandlers(); vi.restoreAllMocks(); });
afterAll(() => { server.close(); });

function renderClassPage() {
  server.use(http.get('/api/v1/reports/courses/course-1/classes/class-1', () => HttpResponse.json(CLASS_REPORT)));
  return renderWithProviders(
    <Routes><Route path="/courses/:courseId/classes/:classId" element={<ClassProgressPage />} /></Routes>,
    { route: '/courses/course-1/classes/class-1' }
  );
}

describe('class progress page', () => {
  it('shows every class student and assignment-level results by default', async () => {
    renderClassPage();

    expect(await screen.findByRole('heading', { name: 'Class A' })).toBeInTheDocument();
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.getByText('2 of 2 students')).toBeInTheDocument();
    expect(screen.getAllByText('String utilities')).toHaveLength(2);
    expect(screen.getByText(/1 failed/)).toBeInTheDocument();
    expect(screen.getByText(/45% best · latest 45%/)).toBeInTheDocument();
    expect(screen.getByText('Latest failed')).toBeInTheDocument();
  });

  it('allows sorting the full roster by column', async () => {
    renderClassPage();
    await screen.findByText('Ada Lovelace');
    const nameColumn = screen.getByRole('columnheader', { name: 'Name' });

    await userEvent.setup().click(nameColumn);
    expect(nameColumn).toHaveAttribute('aria-sort', 'ascending');
    await userEvent.setup().click(nameColumn);
    expect(nameColumn).toHaveAttribute('aria-sort', 'descending');
  });

  it('sorts assignment results numerically', async () => {
    renderClassPage();
    await screen.findByText('Ada Lovelace');
    const grid = screen.getByRole('grid');
    const assignmentColumn = screen.getByRole('columnheader', { name: 'String utilities' });

    await userEvent.setup().click(assignmentColumn);

    const rows = within(grid).getAllByRole('row').slice(1);
    expect(rows[0]).toHaveTextContent('Grace Hopper');
    expect(rows[1]).toHaveTextContent('Ada Lovelace');
  });

  it('searches case-insensitively and filters by progress and enrollment status', async () => {
    renderClassPage();
    const user = userEvent.setup();

    await screen.findByText('Ada Lovelace');
    await user.type(screen.getByRole('textbox', { name: 'Search students' }), 'GRACE');
    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.queryByText('Ada Lovelace')).not.toBeInTheDocument();

    await user.clear(screen.getByRole('textbox', { name: 'Search students' }));
    await user.click(screen.getByRole('combobox', { name: 'Progress' }));
    await user.click(await screen.findByRole('option', { name: 'Not started' }));
    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.queryByText('Ada Lovelace')).not.toBeInTheDocument();

    await user.click(screen.getByRole('combobox', { name: 'Progress' }));
    await user.click(await screen.findByRole('option', { name: 'All progress' }));
    await user.click(screen.getByRole('combobox', { name: 'Enrollment' }));
    await user.click(await screen.findByRole('option', { name: 'Withdrawn' }));
    expect(screen.getByText('Grace Hopper')).toBeInTheDocument();
    expect(screen.queryByText('Ada Lovelace')).not.toBeInTheDocument();
  });

  it('exports the whole class without sending table search or filter state', async () => {
    renderClassPage();
    const user = userEvent.setup();
    let exportUrl = '';
    server.use(http.get('/api/v1/reports/courses/course-1/classes/class-1/export', ({ request }) => {
      exportUrl = new URL(request.url).pathname + new URL(request.url).search;
      return new HttpResponse('workbook', {
        headers: { 'content-type': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }
      });
    }));
    vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:class-report');
    vi.spyOn(URL, 'revokeObjectURL').mockImplementation(() => undefined);
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => undefined);

    await screen.findByText('Ada Lovelace');
    await user.type(screen.getByRole('textbox', { name: 'Search students' }), 'Grace');
    await user.click(screen.getByRole('button', { name: 'Export XLSX' }));

    await waitFor(() => expect(exportUrl).toBe('/api/v1/reports/courses/course-1/classes/class-1/export?format=xlsx'));
    expect(within(screen.getByRole('grid')).getByText('Grace Hopper')).toBeInTheDocument();
  });

  it('shows an actionable empty state when the class has no students', async () => {
    server.use(http.get('/api/v1/reports/courses/course-1/classes/class-1', () => HttpResponse.json({
      ...CLASS_REPORT, students: [], assignments: []
    })));
    renderWithProviders(
      <Routes><Route path="/courses/:courseId/classes/:classId" element={<ClassProgressPage />} /></Routes>,
      { route: '/courses/course-1/classes/class-1' }
    );

    expect(await screen.findByText('No students are enrolled in this class yet.')).toBeInTheDocument();
  });
});
