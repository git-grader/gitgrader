// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { screen } from '@testing-library/react';
import { http, HttpResponse } from 'msw';
import { Route, Routes } from 'react-router';
import { beforeEach, expect, it } from 'vitest';
import { PublicResultPage } from '../src/pages/PublicResultPage';
import { expectNoAxeViolations, renderWithProviders, server } from './harness';

const RESULT = {
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
};

function renderPage(route = '/result/token123') {
  return renderWithProviders(
    <Routes>
      <Route path="/result/:token" element={<PublicResultPage />} />
    </Routes>,
    { route }
  );
}

// A fresh handler per test: a handler left from the first test would answer the later
// ones, so they would pass on cached data rather than on whatever their own handler set
// up.
beforeEach(() => {
  server.use(http.get('/api/v1/results/:token', () => HttpResponse.json(RESULT)));
});

it('strips the hidden tests a result carries', async () => {
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

it('has no a11y violations', async () => {
  const { container } = renderPage();
  await screen.findByText('Test Assig');
  await expectNoAxeViolations(container);
});

// A run that timed out or broke leaves no score, deliberately: the domain refuses to
// write a zero because it would be indistinguishable from a student who passed nothing.
// The page read it as a number regardless, which threw during render and left the
// student a blank page instead of their result.
it('explains a run that produced no score', async () => {
  server.use(http.get('/api/v1/results/:token', () =>
    HttpResponse.json({
      ...RESULT,
      assignmentTitle: 'Timed Out Assig',
      passed: 0,
      total: 0,
      score: null,
      tests: []
    })));

  renderPage('/result/token456');

  await screen.findByText('Timed Out Assig');
  expect(screen.getByText(/has no score yet/)).toBeInTheDocument();
  expect(screen.queryByText(/Score: 0.0 %/)).not.toBeInTheDocument();
});
