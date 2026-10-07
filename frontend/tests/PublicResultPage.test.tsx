// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { PublicResultPage } from '../src/pages/PublicResultPage';
import { QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter, Route, Routes } from 'react-router';
import { expect, it, vi } from 'vitest';
import { createTestQueryClient, expectNoAxeViolations } from './harness';


vi.mock('../src/api', async () => {
  const actual = await vi.importActual<typeof import('../src/api')>('../src/api');
  return {
    ...actual,
    api: {
      ...actual.api,
      getResult: vi.fn().mockResolvedValue({
        assignmentTitle: 'Test Assig',
        courseName: 'Test Course',
        commitSha: 'abcdef12',
        receivedAt: new Date().toISOString(),
        verified: true,
        passed: 1,
        total: 2,
        score: 50.0,
        tests: [
          { public: true, name: 'PubTest', outcome: 'PASSED', message: 'OK' },
          { public: false, name: 'HiddenTestSecret', category: 'Security', outcome: 'FAILED', hint: 'Check constraints', message: 'secret stacktrace' }
        ]
      })
    }
  };
});

// A fresh client per render: a module-level one would carry the first test's cached
// result into the next, so the later tests would pass on cached data rather than on
// whatever their own stub returned.
function renderPage(route = '/result/token123') {
  return render(
    <QueryClientProvider client={createTestQueryClient()}>
      <MemoryRouter initialEntries={[route]}>
        <Routes>
          <Route path="/result/:token" element={<PublicResultPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
}

it('PublicResultPage defensive stripping', async () => {
  renderPage();

  await screen.findByText('Test Assig');

  // Should show public test details
  expect(screen.getByText('PubTest')).toBeInTheDocument();
  expect(screen.getByText('OK')).toBeInTheDocument();

  // Should hide secret names/messages
  expect(screen.queryByText('HiddenTestSecret')).not.toBeInTheDocument();
  expect(screen.queryByText('secret stacktrace')).not.toBeInTheDocument();

  // Should show category and hint
  expect(screen.getByText('Security')).toBeInTheDocument();
  expect(screen.getByText('Check constraints')).toBeInTheDocument();
});

it('PublicResultPage has no a11y violations', async () => {
  const { container } = renderPage();
  await screen.findByText('Test Assig');
  await expectNoAxeViolations(container);
});

// A run that timed out or broke leaves no score, deliberately: the domain refuses to
// write a zero because it would be indistinguishable from a student who passed nothing.
// The page read it as a number regardless, which threw during render and left the
// student a blank page instead of their result.
it('PublicResultPage explains a run that produced no score', async () => {
  const { api } = await import('../src/api');
  (api.getResult as ReturnType<typeof vi.fn>).mockResolvedValueOnce({
    assignmentTitle: 'Timed Out Assig',
    courseName: 'Test Course',
    commitSha: 'abcdef12',
    receivedAt: new Date().toISOString(),
    verified: true,
    passed: 0,
    total: 0,
    score: null,
    tests: []
  });

  renderPage('/result/token456');

  await screen.findByText('Timed Out Assig');
  expect(screen.getByText(/has no score yet/)).toBeInTheDocument();
  expect(screen.queryByText(/Score: 0.0 %/)).not.toBeInTheDocument();
});
