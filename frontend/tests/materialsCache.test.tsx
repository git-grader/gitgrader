// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MaterialsPage } from '../src/pages/MaterialsPage';
import { AssignmentsPage } from '../src/pages/AssignmentsPage';
import { page, renderWithProviders, server } from './harness';

beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

const SUITE = { id: 's1', suiteKey: 'hidden', name: 'Hidden Suite', description: null };

function version(publishedAt: string | null) {
  return {
    id: 'v1', suiteId: 's1', versionLabel: 'v1.0', storagePath: '/s/v1', contentHash: 'abc12345',
    hiddenTestCount: 3, publicTestCount: 2, publishedAt, publishedBy: publishedAt ? 'someone' : null,
    createdAt: '2026-01-01T00:00:00Z'
  };
}

/**
 * The materials page and the assignment form read the same test suite versions, and for a
 * while they filed them under different keys - `testSuites` here, `test-suites` there.
 * Publishing then invalidated a cache nobody read, so the version the instructor had just
 * published was missing from the dropdown they went to next until the page was reloaded.
 *
 * Both pages share one client on purpose. Rendered apart, each passes while the other
 * shows nothing, which is exactly how the bug survived.
 */
test('publishing a test suite version offers it to the assignment form', async () => {
  let published = false;
  server.use(
    http.get('/api/v1/test-suites', () => HttpResponse.json(page([SUITE]))),
    http.get('/api/v1/test-suites/s1/versions', () => HttpResponse.json([version(published ? '2026-02-01T00:00:00Z' : null)])),
    http.post('/api/v1/test-suites/versions/v1/publish', () => {
      published = true;
      return HttpResponse.json(version('2026-02-01T00:00:00Z'));
    }),
    http.get('/api/v1/templates', () => HttpResponse.json(page([]))),
    http.get('/api/v1/runtimes', () => HttpResponse.json([])),
    http.get('/api/v1/courses', () => HttpResponse.json(page([{
      id: 'c1', courseKey: 'cs101', name: 'Course One', description: null, semester: null,
      startsOn: null, endsOn: null, timezone: 'Europe/Zurich', status: 'ACTIVE',
      registrationOpensAt: null, registrationClosesAt: null, registrationEnabled: true
    }]))),
    http.get('/api/v1/assignments', () => HttpResponse.json(page([])))
  );

  const user = userEvent.setup();
  renderWithProviders(<><MaterialsPage /><AssignmentsPage /></>);

  await user.click(await screen.findByRole('button', { name: 'New Assignment' }));
  const before = within(await screen.findByRole('dialog'));
  await user.click(before.getByRole('combobox', { name: 'Test Suite Version' }));
  expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual(['None']);
  await user.keyboard('{Escape}');
  await user.click(before.getByRole('button', { name: 'Cancel' }));

  await user.click(await screen.findByRole('tab', { name: 'Test Suites' }));
  await user.click(await screen.findByRole('button', { name: 'Publish' }));
  await user.click(await screen.findByRole('button', { name: 'Confirm Publish' }));

  await user.click(await screen.findByRole('button', { name: 'New Assignment' }));
  const after = within(await screen.findByRole('dialog'));
  await user.click(after.getByRole('combobox', { name: 'Test Suite Version' }));
  expect(await screen.findByRole('option', { name: /Hidden Suite/ })).toBeInTheDocument();
});
