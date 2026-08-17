// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AssignmentsPage } from '../src/pages/AssignmentsPage';
import { page, renderWithProviders, server } from './harness';

beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

const COURSE = {
  id: 'c1', courseKey: 'cs101', name: 'Course One', description: null, semester: null,
  startsOn: null, endsOn: null, timezone: 'Europe/Zurich', status: 'ACTIVE',
  registrationOpensAt: null, registrationClosesAt: null, registrationEnabled: true
};

/**
 * Opening the assignments page used to cost one request per template and per test suite,
 * on top of the two that listed them, so an established course could issue several
 * hundred and the form waited on every one. The count is asserted rather than the timing
 * because the fan-out reappears the moment someone fetches versions per material again,
 * and that reads as nothing worse than a slow page until a course grows.
 */
test('the assignment form asks for its choices in a fixed number of requests', async () => {
  const materials = Array.from({ length: 40 }, (_, i) => ({
    id: `m${i}`, templateKey: `t${i}`, suiteKey: `s${i}`, name: `Material ${i}`, description: null
  }));
  let requests = 0;
  server.events.on('request:start', () => { requests += 1; });

  server.use(
    http.get('/api/v1/courses', () => HttpResponse.json(page([COURSE]))),
    http.get('/api/v1/assignments', () => HttpResponse.json(page([]))),
    http.get('/api/v1/templates', () => HttpResponse.json(page(materials))),
    http.get('/api/v1/test-suites', () => HttpResponse.json(page(materials))),
    http.get('/api/v1/runtimes', () => HttpResponse.json([])),
    http.get('/api/v1/materials/published', () => HttpResponse.json({
      templateVersions: materials.map(m => ({
        id: `tv-${m.id}`, templateName: m.name, versionLabel: 'v1'
      })),
      suiteVersions: materials.map(m => ({
        id: `sv-${m.id}`, suiteName: m.name, versionLabel: 'v1', hiddenTestCount: 3, publicTestCount: 2
      }))
    })),
    // Any per-material version request is the defect this test exists to catch, so it
    // fails loudly here instead of quietly serving the old shape.
    http.get('/api/v1/templates/:id/versions', () => HttpResponse.error()),
    http.get('/api/v1/test-suites/:id/versions', () => HttpResponse.error())
  );

  const user = userEvent.setup();
  renderWithProviders(<AssignmentsPage />);

  await user.click(await screen.findByRole('button', { name: 'New Assignment' }));
  const dialog = within(await screen.findByRole('dialog'));
  await user.click(dialog.getByRole('combobox', { name: 'Template Version' }));

  // Every published version is still offered: 40 templates plus the empty choice.
  expect(await screen.findByRole('option', { name: /Material 39/ })).toBeInTheDocument();
  expect(screen.getAllByRole('option')).toHaveLength(materials.length + 1);

  // Courses, assignments, the published set and the runtimes. Not one per material.
  expect(requests).toBeLessThanOrEqual(6);
});
