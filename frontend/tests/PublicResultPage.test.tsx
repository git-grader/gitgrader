// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { render, screen } from '@testing-library/react';
import { PublicResultPage } from '../src/pages/PublicResultPage';
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

const queryClient = new QueryClient();

test('PublicResultPage defensive stripping', async () => {
  render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/result/token123']}>
        <Routes>
          <Route path="/result/:token" element={<PublicResultPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  
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

test('PublicResultPage has no a11y violations', async () => {
  const { container } = render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={['/result/token123']}>
        <Routes>
          <Route path="/result/:token" element={<PublicResultPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );
  await screen.findByText('Test Assig');
  await expectNoAxeViolations(container);
});

// A run that timed out or broke leaves no score, deliberately: the domain refuses to
// write a zero because it would be indistinguishable from a student who passed nothing.
// The page read it as a number regardless, which threw during render and left the
// student a blank page instead of their result.
test('PublicResultPage explains a run that produced no score', async () => {
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

  render(
    <QueryClientProvider client={new QueryClient()}>
      <MemoryRouter initialEntries={['/result/token456']}>
        <Routes>
          <Route path="/result/:token" element={<PublicResultPage />} />
        </Routes>
      </MemoryRouter>
    </QueryClientProvider>
  );

  await screen.findByText('Timed Out Assig');
  expect(screen.getByText(/has no score yet/)).toBeInTheDocument();
  expect(screen.queryByText(/Score: 0.0 %/)).not.toBeInTheDocument();
});
