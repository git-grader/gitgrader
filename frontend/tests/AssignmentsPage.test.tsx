// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, expect, test } from 'vitest';
import { http, HttpResponse } from 'msw';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
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

function created(overrides: Record<string, unknown> = {}) {
  return {
    id: 'a1', courseId: 'c1', assignmentKey: 'hw1', title: 'Homework 1', description: null,
    displayOrder: 10, status: 'DRAFT', mandatory: true, opensAt: null, dueAt: null,
    timezone: 'America/New_York', maxPoints: 10, testCount: 0, passThreshold: 0, allowLate: false,
    templateVersionId: null, testSuiteVersionId: null, runtimeId: null, timeoutSeconds: null,
    memoryLimitBytes: null, cpuLimit: null, pidLimit: null, networkEnabled: false,
    ...overrides
  };
}

function stubReads() {
  server.use(
    http.get('/api/v1/courses', () => HttpResponse.json(page([COURSE]))),
    http.get('/api/v1/assignments', () => HttpResponse.json(page([]))),
    http.get('/api/v1/templates', () => HttpResponse.json(page([]))),
    http.get('/api/v1/test-suites', () => HttpResponse.json(page([]))),
    http.get('/api/v1/runtimes', () => HttpResponse.json([]))
  );
}

// Typing a long value one keystroke at a time re-renders the whole form per character,
// which under coverage instrumentation is slow enough to time the test out. Nothing here
// depends on the individual key events.
function setValue(field: HTMLElement, value: string) {
  fireEvent.change(field, { target: { value } });
}

async function openDialog() {
  const user = userEvent.setup();
  renderWithProviders(<AssignmentsPage />);
  await user.click(await screen.findByRole('button', { name: 'New Assignment' }));
  return { user, dialog: within(await screen.findByRole('dialog')) };
}

/**
 * A deadline is stated in the assignment's zone, not the reader's. Converting it through
 * the browser stored an instant hours from the one typed, and `dueAt` is what decides
 * whether a submission counts as late.
 *
 * The suite runs in Europe/Zurich (see `test.env` in vite.config.ts), so a New York
 * assignment is a real six-hour disagreement rather than a no-op.
 */
test('sends a deadline in the assignment timezone rather than the browser one', async () => {
  stubReads();
  let body: unknown;
  server.use(http.post('/api/v1/assignments', async ({ request }) => {
    body = await request.json();
    return HttpResponse.json(created(), { status: 201 });
  }));

  const { user, dialog } = await openDialog();
  setValue(dialog.getByRole('textbox', { name: 'Key' }), 'hw1');
  setValue(dialog.getByRole('textbox', { name: 'Title' }), 'Homework 1');
  setValue(dialog.getByRole('textbox', { name: 'Timezone' }), 'America/New_York');
  setValue(dialog.getByLabelText('Due At'), '2026-03-01T23:59');
  await user.click(dialog.getByRole('button', { name: 'Create' }));

  await waitFor(() => { expect(body).toBeDefined(); });
  // 23:59 in New York on 1 March is 04:59 UTC the next day; reading it as Zurich time
  // would have stored 22:59 UTC on the first.
  expect(body).toMatchObject({ dueAt: '2026-03-02T04:59:00.000Z' });
});

test('does not throw out of the form when a date cannot be read', async () => {
  stubReads();
  let body: unknown;
  server.use(http.post('/api/v1/assignments', async ({ request }) => {
    body = await request.json();
    return HttpResponse.json(created(), { status: 201 });
  }));

  const { user, dialog } = await openDialog();
  setValue(dialog.getByRole('textbox', { name: 'Key' }), 'hw1');
  setValue(dialog.getByRole('textbox', { name: 'Title' }), 'Homework 1');
  setValue(dialog.getByRole('textbox', { name: 'Timezone' }), 'Not/AZone');
  await user.click(dialog.getByRole('button', { name: 'Create' }));

  await waitFor(() => { expect(body).toBeDefined(); });
  expect(screen.getByRole('dialog')).toBeInTheDocument();
});

/**
 * `parseFloat('')` is NaN and the form coerced it with `|| 0`, so clearing "Max Points"
 * created an assignment worth nothing without saying anything about it. What must not
 * happen is a request carrying a zero nobody typed.
 */
test('refuses a cleared points field rather than submitting zero', async () => {
  stubReads();
  const posts: unknown[] = [];
  server.use(http.post('/api/v1/assignments', async ({ request }) => {
    posts.push(await request.json());
    return HttpResponse.json(created(), { status: 201 });
  }));

  const { user, dialog } = await openDialog();
  setValue(dialog.getByRole('textbox', { name: 'Key' }), 'hw1');
  setValue(dialog.getByRole('textbox', { name: 'Title' }), 'Homework 1');
  setValue(dialog.getByRole('spinbutton', { name: 'Max Points' }), '');
  await user.click(dialog.getByRole('button', { name: 'Create' }));

  expect(posts).toEqual([]);
  expect(screen.getByRole('dialog')).toBeInTheDocument();
});

test('names the field when a required choice is missing', async () => {
  server.use(
    http.get('/api/v1/courses', () => HttpResponse.json(page([]))),
    http.get('/api/v1/assignments', () => HttpResponse.json(page([]))),
    http.get('/api/v1/templates', () => HttpResponse.json(page([]))),
    http.get('/api/v1/test-suites', () => HttpResponse.json(page([]))),
    http.get('/api/v1/runtimes', () => HttpResponse.json([]))
  );
  const posts: unknown[] = [];
  server.use(http.post('/api/v1/assignments', async ({ request }) => {
    posts.push(await request.json());
    return HttpResponse.json(created(), { status: 201 });
  }));

  renderWithProviders(<AssignmentsPage />);

  // Without a course there is nothing to attach an assignment to, and the button used to
  // open a dialog whose Create did nothing at all.
  await waitFor(() => { expect(screen.getByRole('button', { name: 'New Assignment' })).toBeDisabled(); });
  expect(posts).toEqual([]);
});

test('keeps a deliberate zero distinguishable from an empty field', async () => {
  stubReads();
  let body: unknown;
  server.use(http.post('/api/v1/assignments', async ({ request }) => {
    body = await request.json();
    return HttpResponse.json(created(), { status: 201 });
  }));

  const { user, dialog } = await openDialog();
  setValue(dialog.getByRole('textbox', { name: 'Key' }), 'hw1');
  setValue(dialog.getByRole('textbox', { name: 'Title' }), 'Homework 1');
  setValue(dialog.getByRole('spinbutton', { name: 'Max Points' }), '0');
  await user.click(dialog.getByRole('button', { name: 'Create' }));

  await waitFor(() => { expect(body).toBeDefined(); });
  expect(body).toMatchObject({ maxPoints: 0 });
});

/**
 * The dialog is mounted for the life of the page, so anything left in it after a failed
 * attempt was still there the next time it opened - including the error banner from a
 * request the instructor had already given up on.
 */
test('opens clean after a failed attempt', async () => {
  stubReads();
  server.use(http.post('/api/v1/assignments', () => new HttpResponse(
    JSON.stringify({ type: 'about:blank', title: 'Conflict', status: 409, detail: 'That key is taken' }),
    { status: 409, headers: { 'content-type': 'application/problem+json' } }
  )));

  const { user, dialog } = await openDialog();
  setValue(dialog.getByRole('textbox', { name: 'Key' }), 'hw1');
  setValue(dialog.getByRole('textbox', { name: 'Title' }), 'Homework 1');
  await user.click(dialog.getByRole('button', { name: 'Create' }));

  expect(await dialog.findByText('That key is taken')).toBeInTheDocument();
  await user.click(dialog.getByRole('button', { name: 'Cancel' }));
  await waitFor(() => { expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); });

  await user.click(screen.getByRole('button', { name: 'New Assignment' }));
  const reopened = within(await screen.findByRole('dialog'));
  expect(reopened.queryByText('That key is taken')).not.toBeInTheDocument();
  expect(reopened.getByRole('textbox', { name: 'Key' })).toHaveValue('');
});
