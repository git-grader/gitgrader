// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CourseDetailPage } from '../src/pages/CourseDetailPage';
import { ReportPage } from '../src/pages/ReportPage';
import { Route, Routes } from 'react-router';
import { renderWithProviders, server } from './harness';

const COURSE = {
  id: 'c1', courseKey: 'cs101', name: 'Course One', description: null, semester: null,
  startsOn: null, endsOn: null, timezone: 'Europe/Zurich', status: 'ACTIVE',
  registrationOpensAt: null, registrationClosesAt: null, registrationEnabled: true
};

function student(overrides: Record<string, unknown> = {}) {
  return {
    studentId: 's1', studentNumber: '001', fullName: 'A Student', fullyCompleted: 0,
    partiallyCompleted: 0, notStarted: 0, completionRate: 0, pointsEarned: 0, pointsRate: 0,
    totalPoints: 0, submissionCount: 0, lastActivityAt: null, assignments: {},
    ...overrides
  };
}

beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

describe('a course that could not be loaded in full', () => {
  /**
   * A failed classes request left the value undefined, which the table treated as neither
   * an empty list nor an error: it rendered no rows and no message, so a course whose
   * classes could not be loaded looked exactly like a course that has none.
   */
  it('says the classes failed rather than showing none', async () => {
    server.use(
      http.get('/api/v1/courses/c1', () => HttpResponse.json(COURSE)),
      http.get('/api/v1/courses/c1/classes', () => new HttpResponse(null, { status: 500 }))
    );

    renderWithProviders(
      <Routes><Route path="/courses/:id" element={<CourseDetailPage />} /></Routes>,
      { route: '/courses/c1' }
    );

    expect(await screen.findByText('The classes for this course could not be loaded.')).toBeInTheDocument();
    expect(screen.queryByText('No classes found.')).not.toBeInTheDocument();
  });

  it('still says none when there genuinely are none', async () => {
    server.use(
      http.get('/api/v1/courses/c1', () => HttpResponse.json(COURSE)),
      http.get('/api/v1/courses/c1/classes', () => HttpResponse.json([]))
    );

    renderWithProviders(
      <Routes><Route path="/courses/:id" element={<CourseDetailPage />} /></Routes>,
      { route: '/courses/c1' }
    );

    expect(await screen.findByText('No classes found.')).toBeInTheDocument();
  });

  it('offers the report the course has, which nothing used to link to', async () => {
    server.use(
      http.get('/api/v1/courses/c1', () => HttpResponse.json(COURSE)),
      http.get('/api/v1/courses/c1/classes', () => HttpResponse.json([]))
    );

    renderWithProviders(
      <Routes><Route path="/courses/:id" element={<CourseDetailPage />} /></Routes>,
      { route: '/courses/c1' }
    );

    expect(await screen.findByRole('link', { name: 'View Report' })).toHaveAttribute('href', '/reports/course/c1');
  });
});

describe('the course report', () => {
  function renderReport(body: Record<string, unknown>) {
    server.use(http.get('/api/v1/reports/courses/c1', () => HttpResponse.json(body)));
    return renderWithProviders(
      <Routes><Route path="/reports/course/:courseId" element={<ReportPage />} /></Routes>,
      { route: '/reports/course/c1' }
    );
  }

  /**
   * The buckets were derived by subtracting two counts from the total. A course with no
   * mandatory assignments satisfies `fullyCompleted === totalMandatoryAssignments` for
   * everyone, including students who have submitted nothing and were counted as not
   * started as well, so the remainder was reported as a negative number of students.
   */
  it('does not report a negative number of students', async () => {
    renderReport({
      courseId: 'c1',
      totalMandatoryAssignments: 0,
      totalPointsAvailable: 0,
      students: [student(), student({ studentId: 's2', studentNumber: '002' })]
    });

    expect(await screen.findByText(/Partially completed: 0 of 2/)).toBeInTheDocument();
    expect(screen.getByText(/Fully completed: 0 of 2/)).toBeInTheDocument();
    expect(screen.getByText(/Not started: 2 of 2/)).toBeInTheDocument();
    expect(screen.getByText(/no mandatory assignments/)).toBeInTheDocument();
  });

  it('counts a course that does have mandatory assignments', async () => {
    renderReport({
      courseId: 'c1',
      totalMandatoryAssignments: 2,
      totalPointsAvailable: 20,
      students: [
        student({ fullyCompleted: 2, submissionCount: 4 }),
        student({ studentId: 's2', studentNumber: '002', fullyCompleted: 1, submissionCount: 1 }),
        student({ studentId: 's3', studentNumber: '003' })
      ]
    });

    expect(await screen.findByText(/Fully completed: 1 of 3/)).toBeInTheDocument();
    expect(screen.getByText(/Partially completed: 1 of 3/)).toBeInTheDocument();
    expect(screen.getByText(/Not started: 1 of 3/)).toBeInTheDocument();
  });

  /**
   * Assigning `window.location` navigated the browser away from the application, so a
   * failed export replaced the page with a raw problem document.
   */
  it('reports a failed export without leaving the page', async () => {
    renderReport({ courseId: 'c1', totalMandatoryAssignments: 1, totalPointsAvailable: 10, students: [student()] });
    server.use(http.get('/api/v1/reports/courses/c1/export', () => new HttpResponse(null, { status: 500 })));

    await userEvent.setup().click(await screen.findByRole('button', { name: 'Export CSV' }));

    await waitFor(() => { expect(screen.getByText('The CSV export failed (500).')).toBeInTheDocument(); });
    expect(screen.getByRole('heading', { name: 'Course Report' })).toBeInTheDocument();
  });
});
