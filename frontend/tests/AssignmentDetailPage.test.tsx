// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import userEvent from '@testing-library/user-event';
import { screen, waitFor, within } from '@testing-library/react';
import { Route, Routes } from 'react-router';
import { AssignmentDetailPage } from '../src/pages/AssignmentDetailPage';
import { expectNoAxeViolations, problem, renderWithProviders, server } from './harness';

export const DRAFT = {
  id: 'a1',
  courseId: 'c1',
  assignmentKey: 'hw01',
  title: 'String utilities',
  description: null,
  displayOrder: 1,
  status: 'DRAFT',
  mandatory: true,
  opensAt: '2026-03-01T08:00:00Z',
  dueAt: '2026-03-15T23:00:00Z',
  timezone: 'Europe/Zurich',
  maxPoints: 100,
  testCount: 10,
  passThreshold: 60,
  allowLate: true,
  templateVersionId: 'tv1',
  testSuiteVersionId: 'sv1',
  runtimeId: 'rt1',
  timeoutSeconds: null,
  memoryLimitBytes: null,
  cpuLimit: null,
  pidLimit: null,
  networkEnabled: false
};

export const MATERIALS = {
  templateVersions: [{ id: 'tv1', templateName: 'Maven Java', versionLabel: 'v3' }],
  suiteVersions: [{ id: 'sv1', suiteName: 'Hidden suite', versionLabel: 'v2', hiddenTestCount: 4, publicTestCount: 6 }]
};

function runtime(id: string, key: string, name: string, enabled: boolean) {
  return {
    id,
    runtimeKey: key,
    displayName: name,
    image: `ghcr.io/gitgrader/${key}`,
    tag: '1',
    imageDigest: `sha256:${id}`,
    installCommand: null,
    testCommand: 'npm ci',
    reportFormat: 'TAP',
    enabled,
    createdAt: '2026-01-01T00:00:00Z',
    updatedAt: '2026-01-01T00:00:00Z'
  };
}

export const RUNTIMES = [
  runtime('rt1', 'node24', 'Node 24', true),
  runtime('rt2', 'python313', 'Python 3.13', false)
];

function extension(overrides: Record<string, unknown> = {}) {
  return {
    id: 'ext1',
    assignmentId: 'a1',
    studentId: 'st1',
    extendedDueAt: '2026-03-20T17:00:00.000Z',
    reason: 'Illness',
    grantedBy: 'instructor-1',
    grantedAt: '2026-03-02T09:00:00Z',
    revokedAt: null,
    revokedBy: null,
    createdAt: '2026-03-02T09:00:00Z',
    ...overrides
  };
}

/**
 * `window.confirm` has no implementation in jsdom, so the revoke path needs it stubbed.
 * Kept local to the tests that revoke rather than stubbed globally, because a global stub
 * would silently answer "yes" for any code path added later.
 */
function stubConfirm(answer: boolean) {
  return vi.spyOn(window, 'confirm').mockReturnValue(answer);
}

function stubCommon(assignment: Record<string, unknown> = DRAFT, extensions: Record<string, unknown>[] = []) {
  server.use(
    http.get('/api/v1/assignments/:id', () => HttpResponse.json(assignment)),
    http.get('/api/v1/assignments/:id/extensions', () => HttpResponse.json(extensions)),
    http.get('/api/v1/materials/published', () => HttpResponse.json(MATERIALS)),
    http.get('/api/v1/runtimes', () => HttpResponse.json(RUNTIMES))
  );
}

function renderPage(route = '/assignments/a1') {
  return renderWithProviders(
    <Routes><Route path="/assignments/:id" element={<AssignmentDetailPage />} /></Routes>,
    { route }
  );
}

it('reports a complete draft as ready to publish', async () => {
  stubCommon();
  renderPage();

  expect(await screen.findByRole('heading', { name: 'Publication readiness' })).toBeInTheDocument();
  expect(await screen.findByText('All checklist items are ready.')).toBeInTheDocument();
  await waitFor(() => expect(screen.getByRole('button', { name: 'Publish' })).toBeEnabled());
});

/**
 * The publish button is disabled for a draft missing materials, and the reason lives in a
 * tooltip. A disabled button cannot be focused, so the tooltip hangs off the wrapping
 * span: without that the explanation is unreachable by keyboard entirely.
 */
it('names every missing prerequisite in the publish tooltip', async () => {
  stubCommon({
    ...DRAFT,
    templateVersionId: null,
    testSuiteVersionId: 'sv1',
    runtimeId: null,
    dueAt: null
  });
  const user = userEvent.setup();
  renderPage();

  const publish = await screen.findByRole('button', { name: 'Publish' });
  await waitFor(() => expect(publish).toBeDisabled());
  const wrapper = publish.closest('span') as HTMLElement;
  await user.hover(wrapper);

  const tooltip = await screen.findByRole('tooltip');
  expect(tooltip).toHaveTextContent('a template version');
  expect(tooltip).toHaveTextContent('a runtime');
  expect(tooltip).toHaveTextContent('a due date after the opening date');
  expect(tooltip).not.toHaveTextContent('a test suite version');
});

/**
 * A failed request is not a missing assignment. The two were reported identically, which
 * sent an instructor looking for something they had deleted rather than reloading.
 */
it('offers a retry rather than claiming the assignment is gone', async () => {
  const user = userEvent.setup();
  let attempts = 0;
  server.use(
    http.get('/api/v1/assignments/:id', () => {
      attempts++;
      return attempts === 1
        ? problem(500, { title: 'Internal Server Error', detail: 'Database unavailable' })
        : HttpResponse.json(DRAFT);
    }),
    http.get('/api/v1/assignments/:id/extensions', () => HttpResponse.json([])),
    http.get('/api/v1/materials/published', () => HttpResponse.json(MATERIALS)),
    http.get('/api/v1/runtimes', () => HttpResponse.json(RUNTIMES))
  );
  renderPage();

  expect(await screen.findByText('The assignment could not be loaded.')).toBeInTheDocument();
  await user.click(screen.getByRole('button', { name: /retry/i }));
  expect(await screen.findByRole('heading', { name: 'Publication readiness' })).toBeInTheDocument();
});

it('reports that the material choices could not be loaded at all', async () => {
  server.use(
    http.get('/api/v1/assignments/:id', () => HttpResponse.json(DRAFT)),
    http.get('/api/v1/assignments/:id/extensions', () => HttpResponse.json([])),
    http.get('/api/v1/materials/published', () => problem(503, { title: 'Unavailable', detail: 'down' })),
    http.get('/api/v1/runtimes', () => HttpResponse.json(RUNTIMES))
  );
  renderPage();

  expect(
    await screen.findByText(/templates, test suites and runtimes could not be loaded/)
  ).toBeInTheDocument();
});

/**
 * Dates on this form mean wall-clock time in the assignment's own zone. The save request
 * is where that distinction is observable, so the assertion is on the request body rather
 * than on the rendered input.
 */
it('saves the deadlines as instants in the assignment timezone', async () => {
  let body: Record<string, unknown> | undefined;
  stubCommon();
  server.use(
    http.put('/api/v1/assignments/:id', async ({ request }) => {
      body = (await request.json()) as Record<string, unknown>;
      return HttpResponse.json({ ...DRAFT, ...body });
    })
  );
  const user = userEvent.setup();
  renderPage();

  await screen.findByRole('heading', { name: 'Configuration' });
  const dueAt = screen.getByLabelText('Due At');
  await user.clear(dueAt);
  await user.type(dueAt, '2026-03-20T18:00');
  await user.click(screen.getByRole('button', { name: 'Save Configuration' }));

  expect(await screen.findByText('Assignment updated successfully.')).toBeInTheDocument();
  // 18:00 in Zurich is 17:00 UTC in March, before the zone moves.
  expect(body?.dueAt).toBe('2026-03-20T17:00:00.000Z');
  expect(body?.opensAt).toBe('2026-03-01T08:00:00.000Z');
});

it('refuses a pass threshold outside 0 to 100 without calling the server', async () => {
  let updates = 0;
  stubCommon();
  server.use(
    http.put('/api/v1/assignments/:id', () => {
      updates++;
      return HttpResponse.json(DRAFT);
    })
  );
  const user = userEvent.setup();
  renderPage();

  await screen.findByRole('heading', { name: 'Configuration' });
  const threshold = screen.getByRole('spinbutton', { name: 'Pass Threshold' });
  await user.clear(threshold);
  await user.type(threshold, '140');
  await user.click(screen.getByRole('button', { name: 'Save Configuration' }));

  expect(
    await screen.findByText('Enter non-negative points and test count, and a pass threshold from 0 to 100.')
  ).toBeInTheDocument();
  expect(updates).toBe(0);
});

it('publishes a draft and reflects the published status in the form', async () => {
  stubCommon();
  server.use(
    http.post('/api/v1/assignments/:id/publish', () =>
      HttpResponse.json({ ...DRAFT, status: 'PUBLISHED' })
    )
  );
  const user = userEvent.setup();
  renderPage();

  await waitFor(() => expect(screen.getByRole('button', { name: 'Publish' })).toBeEnabled());
  await user.click(screen.getByRole('button', { name: 'Publish' }));

  expect(
    await screen.findByText('Published assignments are immutable. Configuration cannot be changed.')
  ).toBeInTheDocument();
  expect(screen.getByRole('spinbutton', { name: 'Max Points' })).toBeDisabled();
  // The response is the authority for the new status. React Query matches keys by prefix
  // and `["assignments"]` is the bare prefix the detail key sits under, so an invalidation
  // scoped to that prefix discarded this write and refetched - leaving the page showing a
  // draft after a successful publish, with no error and nothing to retry.
  expect(screen.getByRole('button', { name: 'Published' })).toBeInTheDocument();
});

it('locks the configuration of an already published assignment', async () => {
  stubCommon({ ...DRAFT, status: 'PUBLISHED' });
  renderPage();

  expect(await screen.findByRole('spinbutton', { name: 'Max Points' })).toBeDisabled();
  expect(screen.getByRole('button', { name: 'Published' })).toBeDisabled();
});

it('grants a deadline extension and clears the form afterwards', async () => {
  let granted: Record<string, unknown> | undefined;
  stubCommon();
  server.use(
    http.post('/api/v1/assignments/:id/extensions', async ({ request }) => {
      granted = (await request.json()) as Record<string, unknown>;
      return HttpResponse.json(extension({ extendedDueAt: '2026-03-20T17:00:00.000Z' }), { status: 201 });
    }),
    // The mutation invalidates the list, so the row has to come back from the refetch -
    // otherwise the assertion would pass against a table that had simply been emptied.
    http.get('/api/v1/assignments/:id/extensions', () =>
      HttpResponse.json([extension({ extendedDueAt: '2026-03-20T17:00:00.000Z' })])
    )
  );
  const user = userEvent.setup();
  renderPage();

  await screen.findByRole('heading', { name: 'Deadline Extensions' });
  await user.type(screen.getByRole('textbox', { name: 'Student ID' }), 'st1');
  await user.type(screen.getByLabelText(/^Extended Due At/), '2026-03-20T18:00');
  await user.type(screen.getByRole('textbox', { name: 'Reason' }), 'Illness');
  await user.click(screen.getByRole('button', { name: 'Grant extension' }));

  expect(await screen.findByText('Illness')).toBeInTheDocument();
  expect(granted?.studentId).toBe('st1');
  expect(screen.getByRole('textbox', { name: 'Student ID' })).toHaveValue('');
  expect(screen.getByRole('textbox', { name: 'Reason' })).toHaveValue('');
});

/**
 * Revoking is the one irreversible action on the page, so it asks first. A declined
 * confirm must leave the extension in place: the guard being tested is the caller's
 * decision, not the presence of the dialog.
 */
it('keeps an extension when the confirmation is declined', async () => {
  let revokes = 0;
  stubCommon(DRAFT, [extension()]);
  server.use(
    http.delete('/api/v1/assignments/:id/extensions/:extensionId', () => {
      revokes++;
      return new HttpResponse(null, { status: 204 });
    })
  );
  stubConfirm(false);
  const user = userEvent.setup();
  renderPage();

  await user.click(await screen.findByRole('button', { name: 'Revoke' }));
  expect(revokes).toBe(0);
  expect(screen.getByRole('button', { name: 'Revoke' })).toBeInTheDocument();
});

it('revokes an extension once the confirmation is accepted', async () => {
  let revokedId: string | undefined;
  stubCommon(DRAFT, [extension()]);
  server.use(
    http.delete('/api/v1/assignments/:id/extensions/:extensionId', ({ params }) => {
      revokedId = params.extensionId as string;
      return new HttpResponse(null, { status: 204 });
    })
  );
  stubConfirm(true);
  const user = userEvent.setup();
  renderPage();

  await user.click(await screen.findByRole('button', { name: 'Revoke' }));
  await waitFor(() => expect(revokedId).toBe('ext1'));
});

it('reports a failed extension load without hiding the grant form', async () => {
  stubCommon();
  server.use(
    http.get('/api/v1/assignments/:id/extensions', () =>
      problem(500, { title: 'Internal Server Error', detail: 'Extension table missing' })
    )
  );
  renderPage();

  expect(await screen.findByText('The extensions could not be loaded.')).toBeInTheDocument();
  expect(screen.getByRole('button', { name: 'Grant extension' })).toBeInTheDocument();
});

it('has no a11y violations', async () => {
  stubCommon(DRAFT, [extension({ revokedAt: '2026-03-03T10:00:00Z' })]);
  const { container } = renderPage();
  await screen.findByRole('heading', { name: 'Deadline Extensions' });
  await waitFor(() => expect(within(container).getByText('Illness')).toBeInTheDocument());
  await expectNoAxeViolations(container);
});
