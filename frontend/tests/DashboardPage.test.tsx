// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { DashboardPage } from '../src/pages/DashboardPage';
import { renderWithProviders, server } from './harness';

const COURSE = {
  id: 'course-1', courseKey: 'cs101', name: 'Programming 101', description: null, semester: 'Autumn',
  startsOn: null, endsOn: null, timezone: 'UTC', status: 'ACTIVE', registrationOpensAt: null,
  registrationClosesAt: null, registrationEnabled: true
};

const PAGE = (content: unknown[]) => ({ content, totalElements: content.length, totalPages: 1, size: 100, number: 0 });

function installSummaryHandlers() {
  server.use(
    http.get('/api/v1/dashboard', () => HttpResponse.json({
      courseCount: 1, studentCount: 2, openAssignmentCount: 1, runningGradingCount: 0, failedInfrastructureCount: 0
    })),
    http.get('/api/v1/courses', () => HttpResponse.json(PAGE([COURSE]))),
    http.get('/api/v1/courses/course-1/classes', () => HttpResponse.json([
      { id: 'class-1', courseId: 'course-1', classKey: 'a', name: 'Class A' }
    ])),
    http.get('/api/v1/reports/courses/course-1', () => HttpResponse.json({
      courseId: 'course-1', totalMandatoryAssignments: 1, totalPointsAvailable: 10,
      students: [
        { studentId: 's1', studentUsername: 'a001', fullName: 'Ada', fullyCompleted: 1, partiallyCompleted: 0,
          notStarted: 0, completionRate: 1, pointsEarned: 8, pointsRate: 0.8, totalPoints: 10,
          submissionCount: 2, lastActivityAt: null, assignments: {} },
        { studentId: 's2', studentUsername: 'g002', fullName: 'Grace', fullyCompleted: 0, partiallyCompleted: 1,
          notStarted: 0, completionRate: 0.5, pointsEarned: 5, pointsRate: 0.5, totalPoints: 10,
          submissionCount: 1, lastActivityAt: null, assignments: {} }
      ]
    })),
    http.get('/api/v1/reports/courses/course-1/classes/class-1', () => HttpResponse.json({
      courseId: 'course-1', classId: 'class-1', classKey: 'a', className: 'Class A',
      totalMandatoryAssignments: 1, totalPointsAvailable: 10, assignments: [],
      students: [
        { studentId: 's1', studentUsername: 'a001', fullName: 'Ada', status: 'VERIFIED_BY_INSTRUCTOR',
          enrollmentStatus: 'ACTIVE', fullyCompleted: 1, partiallyCompleted: 0, notStarted: 0,
          completionRate: 1, pointsEarned: 8, pointsRate: 0.8, totalPoints: 10, submissionCount: 2,
          lastActivityAt: null, assignments: {} },
        { studentId: 's2', studentUsername: 'g002', fullName: 'Grace', status: 'SELF_REGISTERED',
          enrollmentStatus: 'ACTIVE', fullyCompleted: 0, partiallyCompleted: 1, notStarted: 0,
          completionRate: 0.5, pointsEarned: 5, pointsRate: 0.5, totalPoints: 10, submissionCount: 1,
          lastActivityAt: null, assignments: {} }
      ]
    }))
  );
}

beforeEach(() => { server.use(http.get('/api/v1/students', () => HttpResponse.json(PAGE([])))); });

beforeAll(() => { server.listen({ onUnhandledFrame: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

function renderDashboard() {
  return renderWithProviders(
    <Routes><Route path="/dashboard" element={<DashboardPage />} /></Routes>,
    { route: '/dashboard' }
  );
}

describe('dashboard course summaries', () => {
  it('frames the dashboard as an operational overview', async () => {
    installSummaryHandlers();
    renderDashboard();

    expect(await screen.findByRole('heading', { level: 1, name: 'Dashboard' })).toBeInTheDocument();
    expect(screen.getByText('Monitor course activity and follow up where attention is needed.')).toBeInTheDocument();
  });

  it('shows available course, student, submission, completion, and class summaries', async () => {
    installSummaryHandlers();
    renderDashboard();

    expect(await screen.findByRole('heading', { name: 'Courses available' })).toBeInTheDocument();
    expect(await screen.findByRole('link', { name: 'Programming 101' })).toHaveAttribute('href', '/courses/course-1');
    expect(screen.getByText('2 students')).toBeInTheDocument();
    expect(screen.getByText('3 submissions')).toBeInTheDocument();
    expect(screen.getByText('75% average completion')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Class A/ })).toHaveAttribute('href', '/courses/course-1/classes/class-1');
    expect(screen.queryByText(/Assigned courses/)).not.toBeInTheDocument();
  });

  it('shows a genuine empty state when no active courses are available', async () => {
    server.use(
      http.get('/api/v1/dashboard', () => HttpResponse.json({
        courseCount: 0, studentCount: 0, openAssignmentCount: 0, runningGradingCount: 0, failedInfrastructureCount: 0
      })),
      http.get('/api/v1/courses', () => HttpResponse.json(PAGE([])))
    );
    renderDashboard();

    expect(await screen.findByText('No active courses are available.')).toBeInTheDocument();
  });

  it('reports course summary failures with a retry action', async () => {
    server.use(
      http.get('/api/v1/dashboard', () => HttpResponse.json({
        courseCount: 1, studentCount: 2, openAssignmentCount: 1, runningGradingCount: 0, failedInfrastructureCount: 0
      })),
      http.get('/api/v1/courses', () => new HttpResponse(null, { status: 503 }))
    );
    renderDashboard();

    expect(await screen.findByText('Available course summaries could not be loaded.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument();
  });
});

it('links the follow-up dashboard to pending registrations and matching active class filters', async () => {
  installSummaryHandlers();
  const pending = { id: 'pending', studentUsername: 'pending', firstName: 'Pending', lastName: 'Student', email: 'test@example.org', status: 'SELF_REGISTERED' };
  const result = { bestPercent: 0, bestPoints: 0, latestSubmission: { id: 'sub', commitSha: 'sha', gitRef: 'main', receivedAt: '', status: 'FAILED', late: false } };
  const student = { studentId: 's', studentUsername: 's', fullName: 'Student', status: 'SELF_REGISTERED', enrollmentStatus: 'ACTIVE', fullyCompleted: 0, partiallyCompleted: 1, notStarted: 1, completionRate: 0, pointsEarned: 0, pointsRate: 0, totalPoints: 10, submissionCount: 1, assignments: { strings: result } };
  server.use(
    http.get('/api/v1/students', () => HttpResponse.json(PAGE([pending, { ...pending, id: 'verified', status: 'VERIFIED_BY_INSTRUCTOR' }]))),
    http.get('/api/v1/reports/courses/course-1/classes/class-1', () => HttpResponse.json({
      courseId: 'course-1', classId: 'class-1', classKey: 'a', className: 'Class A', totalMandatoryAssignments: 2,
      totalPointsAvailable: 10, assignments: [], students: [student, { ...student, studentId: 'withdrawn', enrollmentStatus: 'WITHDRAWN' }]
    }))
  );
  renderDashboard();
  expect(await screen.findByRole('link', { name: 'Review pending registrations (1)' })).toHaveAttribute('href', '/students?status=SELF_REGISTERED');
  expect(await screen.findByRole('link', { name: 'Review missing work (1)' })).toHaveAttribute('href', '/courses/course-1/classes/class-1?attention=missing&enrollment=ACTIVE');
  expect(screen.getByRole('link', { name: 'Review failed attempts (1)' })).toHaveAttribute('href', '/courses/course-1/classes/class-1?attention=failed&enrollment=ACTIVE');
  expect(screen.getByRole('link', { name: 'Upcoming deadlines' })).toHaveAttribute('href', '/deadlines');
});
