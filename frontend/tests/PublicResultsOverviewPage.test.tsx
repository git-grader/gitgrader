// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { beforeEach, expect, it } from 'vitest';
import { PublicResultsOverviewPage } from '../src/pages/PublicResultsOverviewPage';
import { expectNoAxeViolations, renderWithProviders, server } from './harness';

const OVERVIEW = {
  studentDisplayName: 'Max Muster',
  generatedAt: '2026-07-30T12:00:00Z',
  courses: [
    {
      courseId: 'course-1',
      courseName: 'Example Programming',
      classes: [
        {
          classId: 'class-1',
          className: 'Class A',
          assignments: [
            {
              assignmentId: 'assignment-1',
              assignmentKey: 'assignment-01',
              title: 'String utilities',
              latest: {
                submissionId: 'submission-1',
                shortCommitSha: '454d5a6',
                receivedAt: '2026-07-30T12:00:00Z',
                late: false,
                submissionStatus: 'PASSED',
                gradingStatus: 'COMPLETED',
                testsPassed: 7,
                testsTotal: 10,
                scorePercent: 70.0,
                passed: true,
                resultUrl:
                  'https://grader.example.org/results/overview/token123/submissions/submission-1'
              }
            },
            {
              assignmentId: 'assignment-2',
              assignmentKey: 'assignment-02',
              title: 'Collections',
              latest: null
            }
          ]
        }
      ]
    }
  ]
};

beforeEach(() => {
  server.use(
    http.get('/api/v1/results/overview/:token', () => HttpResponse.json(OVERVIEW))
  );
});

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route path="/results/overview/:token" element={<PublicResultsOverviewPage />} />
    </Routes>,
    { route: '/results/overview/token123' }
  );
}

it('lists grouped attempts and links to scoped reports', async () => {
  renderPage();

  await screen.findByText('Example Programming');

  expect(screen.getByText('Class A')).toBeInTheDocument();
  expect(screen.getByText('String utilities')).toBeInTheDocument();
  expect(screen.getByText('70.0 %')).toBeInTheDocument();
  expect(screen.getByText('No attempt yet.')).toBeInTheDocument();

  const reportLink = screen.getByRole('link', { name: /view report/i });
  expect(reportLink).toHaveAttribute(
    'href',
    '/results/overview/token123/submissions/submission-1'
  );
});

it('has no a11y violations', async () => {
  const { container } = renderPage();
  await screen.findByText('Example Programming');
  await expectNoAxeViolations(container);
});
