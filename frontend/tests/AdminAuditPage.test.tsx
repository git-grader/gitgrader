// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import userEvent from '@testing-library/user-event';
import { screen, waitFor } from '@testing-library/react';
import { Routes, Route } from 'react-router';
import { AdminAuditPage } from '../src/pages/AdminAuditPage';
import { expectNoAxeViolations, page, problem, renderWithProviders, server } from './harness';

const EVENT = {
  id: 'e1',
  occurredAt: '2026-03-01T10:00:00Z',
  eventType: 'RATE_LIMIT_TRIGGERED',
  severity: 'WARNING',
  actorType: 'ANONYMOUS',
  actorId: null,
  actorName: null,
  subjectType: 'SUBMISSION',
  subjectId: 's1',
  courseId: 'c1',
  outcome: 'REJECTED',
  sourceIpHash: 'hash',
  correlationId: 'corr-1',
  detail: { limit: 'submissions/hour', decision: 'rejected', window: { hour: 3 }, skipped: null }
};

function renderPage(route = '/admin/audit') {
  return renderWithProviders(
    <Routes><Route path="/admin/audit" element={<AdminAuditPage />} /></Routes>,
    { route }
  );
}

/**
 * The detail column is arbitrary JSON written by whichever call site raised the event, so
 * the rendering has to survive a shape nobody anticipated. `window` here is an object:
 * `String({ hour: 3 })` would print `[object Object]`, which is exactly the detail the
 * reader opened the page for.
 */
it('renders audit detail with nested values serialised rather than stringified', async () => {
  let requested: URL | undefined;
  server.use(
    http.get('/api/v1/audit', ({ request }) => {
      requested = new URL(request.url);
      return HttpResponse.json(page([EVENT]));
    })
  );

  renderPage();

  expect(await screen.findByRole('gridcell', { name: 'RATE_LIMIT_TRIGGERED' })).toBeInTheDocument();
  const detail = await screen.findByText(/limit=submissions\/hour/);
  expect(detail).toHaveTextContent('decision=rejected');
  expect(detail).toHaveTextContent('window={"hour":3}');
  // A null detail value is absent rather than rendered as `skipped=null`.
  expect(detail).not.toHaveTextContent('skipped');
  expect(requested?.searchParams.get('sort')).toBe('occurredAt,desc');
});

it('sends the event type and actor type filters and drops them when cleared', async () => {
  const requested: string[] = [];
  server.use(
    http.get('/api/v1/audit', ({ request }) => {
      requested.push(new URL(request.url).search);
      return HttpResponse.json(page([EVENT]));
    })
  );

  renderPage();
  await screen.findByRole('gridcell', { name: 'RATE_LIMIT_TRIGGERED' });
  const user = userEvent.setup();

  await user.click(await screen.findByRole('combobox', { name: 'Event Type' }));
  await user.click(await screen.findByRole('option', { name: 'SSH_KEY_REVOKED' }));
  await waitFor(() => expect(requested.at(-1)).toContain('eventType=SSH_KEY_REVOKED'));

  await user.click(screen.getByRole('combobox', { name: 'Actor Type' }));
  await user.click(await screen.findByRole('option', { name: 'ADMIN' }));
  await waitFor(() => expect(requested.at(-1)).toContain('actorType=ADMIN'));

  await user.click(screen.getByRole('combobox', { name: 'Event Type' }));
  await user.click(await screen.findByRole('option', { name: 'All events' }));
  await waitFor(() => expect(requested.at(-1)).not.toContain('eventType='));
  expect(requested.at(-1)).toContain('actorType=ADMIN');
});

/**
 * The notice used to render above a grid that rendered anyway, so a reader got a failure
 * message and an empty table at once with no way to try again. The absence of the grid is
 * the half of that which is easy to regress, so it is what is pinned here.
 */
it('offers a retry instead of an empty table when the log cannot be loaded', async () => {
  let attempts = 0;
  server.use(
    http.get('/api/v1/audit', () => {
      attempts++;
      return attempts === 1
        ? problem(503, { title: 'Service Unavailable', detail: 'Audit store unreachable' })
        : HttpResponse.json(page([EVENT]));
    })
  );

  renderPage();
  const user = userEvent.setup();

  expect(await screen.findByText('The audit log could not be loaded.')).toBeInTheDocument();
  expect(screen.queryByRole('grid')).not.toBeInTheDocument();

  await user.click(screen.getByRole('button', { name: /retry/i }));
  expect(await screen.findByRole('gridcell', { name: 'RATE_LIMIT_TRIGGERED' })).toBeInTheDocument();
  expect(screen.getByRole('grid')).toBeInTheDocument();
});

it('reads a filter already present in the address', async () => {
  let requested: URL | undefined;
  server.use(
    http.get('/api/v1/audit', ({ request }) => {
      requested = new URL(request.url);
      return HttpResponse.json(page([EVENT]));
    })
  );

  renderPage('/admin/audit?eventType=SUBMISSION_REJECTED&actorType=STUDENT');

  await screen.findByRole('gridcell', { name: 'RATE_LIMIT_TRIGGERED' });
  expect(requested?.searchParams.get('eventType')).toBe('SUBMISSION_REJECTED');
  expect(requested?.searchParams.get('actorType')).toBe('STUDENT');
});

it('has no a11y violations', async () => {
  server.use(http.get('/api/v1/audit', () => HttpResponse.json(page([EVENT]))));

  const { container } = renderPage();
  await screen.findByRole('gridcell', { name: 'RATE_LIMIT_TRIGGERED' });
  await expectNoAxeViolations(container);
});
