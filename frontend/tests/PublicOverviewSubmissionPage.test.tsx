// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { beforeEach, expect, it } from 'vitest';
import { PublicOverviewSubmissionPage } from '../src/pages/PublicOverviewSubmissionPage';
import { expectNoAxeViolations, renderWithProviders, server } from './harness';

const RESULT = {
  assignmentTitle: 'String utilities',
  courseName: 'Example Programming',
  commitSha: 'abcdef12',
  receivedAt: '2026-07-30T12:00:00Z',
  verified: true,
  passed: 7,
  total: 10,
  score: 70.0,
  tests: [
    { public: true, name: 'PubTest', outcome: 'PASSED', message: 'OK' },
    {
      public: false,
      name: 'HiddenTestSecret',
      category: 'Security',
      outcome: 'FAILED',
      hint: 'Check constraints',
      message: 'secret stacktrace'
    }
  ]
};

function renderPage() {
  return renderWithProviders(
    <Routes>
      <Route
        path="/results/overview/:token/submissions/:submissionId"
        element={<PublicOverviewSubmissionPage />}
      />
    </Routes>,
    { route: '/results/overview/token123/submissions/submission-1' }
  );
}

beforeEach(() => {
  server.use(
    http.get('/api/v1/results/overview/:token/submissions/:submissionId', () =>
      HttpResponse.json(RESULT))
  );
});

it('fetches and redacts the scoped result', async () => {
  renderPage();

  await screen.findByText('String utilities');

  expect(screen.getByText('PubTest')).toBeInTheDocument();
  expect(screen.queryByText('HiddenTestSecret')).not.toBeInTheDocument();
  expect(screen.queryByText('secret stacktrace')).not.toBeInTheDocument();
  expect(screen.getByText('Security')).toBeInTheDocument();
  expect(screen.getByText('Check constraints')).toBeInTheDocument();

  const backLinks = screen.getAllByRole('link', { name: /back to all results/i });
  expect(backLinks[0]).toHaveAttribute('href', '/results/overview/token123');
});

it('has no a11y violations', async () => {
  const { container } = renderPage();
  await screen.findByText('String utilities');
  await expectNoAxeViolations(container);
});
