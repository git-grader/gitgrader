// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { PublicOverviewSubmissionPage } from '../src/pages/PublicOverviewSubmissionPage';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router';
import { expect, test, vi } from 'vitest';
import { expectNoAxeViolations } from './harness';

vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api');
  return {
    ...actual,
    api: {
      ...actual.api,
      getOverviewSubmission: vi.fn().mockResolvedValue({
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
      })
    }
  };
});

function renderPage() {
  return render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/results/overview/token123/submissions/submission-1']}>
        <Routes>
          <Route
            path="/results/overview/:token/submissions/:submissionId"
            element={<PublicOverviewSubmissionPage />}
          />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

test('PublicOverviewSubmissionPage fetches and redacts the scoped result', async () => {
  renderPage();

  await screen.findByText('String utilities');

  const { api } = await import('../src/api');
  expect(api.getOverviewSubmission).toHaveBeenCalledWith('token123', 'submission-1');

  expect(screen.getByText('PubTest')).toBeInTheDocument();
  expect(screen.queryByText('HiddenTestSecret')).not.toBeInTheDocument();
  expect(screen.queryByText('secret stacktrace')).not.toBeInTheDocument();
  expect(screen.getByText('Security')).toBeInTheDocument();
  expect(screen.getByText('Check constraints')).toBeInTheDocument();

  const backLinks = screen.getAllByRole('link', { name: /back to all results/i });
  expect(backLinks[0]).toHaveAttribute('href', '/results/overview/token123');
});

test('PublicOverviewSubmissionPage has no a11y violations', async () => {
  const { container } = renderPage();
  await screen.findByText('String utilities');
  await expectNoAxeViolations(container);
});
