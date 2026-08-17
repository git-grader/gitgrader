// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { CoursesPage } from '../src/pages/CoursesPage';
import { AssignmentsPage } from '../src/pages/AssignmentsPage';
import { MaterialsPage } from '../src/pages/MaterialsPage';
import { expectNoAxeViolations, page, renderWithProviders, server } from './harness';

const COURSE = {
  id: 'c1', courseKey: 'cs101', name: 'Course One', description: null, semester: null,
  startsOn: null, endsOn: null, timezone: 'Europe/Zurich', status: 'ACTIVE',
  registrationOpensAt: null, registrationClosesAt: null, registrationEnabled: true
};

/**
 * Only the two unauthenticated pages were ever checked, which is why every unnamed
 * combobox and unassociated tab panel lived on the instructor side.
 */
describe('the instructor pages', () => {
  beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
  afterEach(() => { server.resetHandlers(); });
  afterAll(() => { server.close(); });

  function stubReads() {
    server.use(
      http.get('/api/v1/courses', () => HttpResponse.json(page([COURSE]))),
      http.get('/api/v1/assignments', () => HttpResponse.json(page([]))),
      http.get('/api/v1/templates', () => HttpResponse.json(page([]))),
      http.get('/api/v1/test-suites', () => HttpResponse.json(page([]))),
      http.get('/api/v1/materials/published', () => HttpResponse.json({ templateVersions: [], suiteVersions: [] })),
      http.get('/api/v1/runtimes', () => HttpResponse.json([]))
    );
  }

  it('lists courses without accessibility violations', async () => {
    stubReads();
    const { container } = renderWithProviders(<CoursesPage />);
    await screen.findByText('Course One');
    await expectNoAxeViolations(container);
  });

  it('names the course filter so it can be reached by its label', async () => {
    stubReads();
    renderWithProviders(<CoursesPage />);
    expect(await screen.findByRole('combobox', { name: 'Status Filter' })).toBeInTheDocument();
  });

  it('opens the new assignment dialog without accessibility violations', async () => {
    stubReads();
    const user = userEvent.setup();
    const { baseElement } = renderWithProviders(<AssignmentsPage />);
    await user.click(await screen.findByRole('button', { name: 'New Assignment' }));
    await screen.findByRole('dialog');
    await expectNoAxeViolations(baseElement);
  });

  it('names every choice in the assignment dialog', async () => {
    stubReads();
    const user = userEvent.setup();
    renderWithProviders(<AssignmentsPage />);
    await user.click(await screen.findByRole('button', { name: 'New Assignment' }));
    const dialog = within(await screen.findByRole('dialog'));

    for (const name of ['Course', 'Template Version', 'Test Suite Version', 'Runtime']) {
      expect(dialog.getByRole('combobox', { name })).toBeInTheDocument();
    }
  });

  it('shows materials without accessibility violations', async () => {
    stubReads();
    const { container } = renderWithProviders(<MaterialsPage />);
    await screen.findByRole('heading', { name: 'Materials' });
    await expectNoAxeViolations(container);
  });

  /**
   * MUI does not associate a tab with the panel it controls on its own, so a reader who
   * moved to the panel had no way to tell which tab had produced it.
   */
  it('ties each materials tab to the panel it controls', async () => {
    stubReads();
    renderWithProviders(<MaterialsPage />);

    const tab = await screen.findByRole('tab', { name: 'Templates' });
    const panelId = tab.getAttribute('aria-controls');
    expect(panelId).toBeTruthy();
    const panel = document.getElementById(panelId ?? '');
    expect(panel?.getAttribute('aria-labelledby')).toBe(tab.id);
  });
});
