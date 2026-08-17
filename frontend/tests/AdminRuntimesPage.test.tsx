// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, describe, expect, it } from 'vitest';
import { http, HttpResponse } from 'msw';
import { fireEvent, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { AdminRuntimesPage } from '../src/pages/AdminRuntimesPage';
import { REPORT_FORMATS } from '../src/api';
import { renderWithProviders, server } from './harness';

/**
 * A runtime is required before any assignment can be published, so a create form the
 * server refuses is not a cosmetic problem: it stops an instance being set up at all.
 * The form used to prefill `JUNIT`, which is not one of the values the server accepts,
 * and offered free text rather than the list, so nothing on screen said what would be.
 */
describe('runtime creation', () => {
  beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
  afterEach(() => { server.resetHandlers(); });
  afterAll(() => { server.close(); });

  const admin = { username: 'admin', displayName: 'Admin', actorType: 'HUMAN', roles: ['ROLE_ADMIN'] };

  // Typing a sha256 digest one keystroke at a time re-renders the form 71 times, which
  // under coverage instrumentation is slow enough to time the test out. Nothing here
  // depends on the individual key events.
  function setValue(field: HTMLElement, value: string) {
    fireEvent.change(field, { target: { value } });
  }

  function stubReads() {
    server.use(
      http.get('/api/v1/me', () => HttpResponse.json(admin)),
      http.get('/api/v1/runtimes', () => HttpResponse.json([]))
    );
  }

  it('offers only report formats the server defines', async () => {
    stubReads();
    const user = userEvent.setup();
    renderWithProviders(<AdminRuntimesPage />);

    await user.click(await screen.findByRole('button', { name: 'New Runtime' }));
    // Queried by accessible name on purpose: the control is only reachable this way if
    // the label is wired to it, which a bare Select without a labelId is not.
    await user.click(screen.getByRole('combobox', { name: 'Report Format' }));

    const offered = screen.getAllByRole('option').map((option) => option.textContent);
    expect(offered).toEqual([...REPORT_FORMATS]);
  });

  it('sends a report format the server accepts', async () => {
    stubReads();
    let body: unknown;
    server.use(
      http.post('/api/v1/runtimes', async ({ request }) => {
        body = await request.json();
        return HttpResponse.json({
          id: 'r1', runtimeKey: 'python', displayName: 'Python', image: 'python', tag: '3.13',
          imageDigest: `sha256:${'a'.repeat(64)}`, installCommand: null, testCommand: 'pytest',
          reportFormat: 'JUNIT_XML', enabled: true,
          createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z'
        }, { status: 201 });
      })
    );

    const user = userEvent.setup();
    renderWithProviders(<AdminRuntimesPage />);
    await user.click(await screen.findByRole('button', { name: 'New Runtime' }));

    setValue(screen.getByRole('textbox', { name: 'Key' }), 'python');
    setValue(screen.getByRole('textbox', { name: 'Display Name' }), 'Python');
    setValue(screen.getByRole('textbox', { name: 'Image' }), 'python');
    setValue(screen.getByRole('textbox', { name: 'Tag' }), '3.13');
    setValue(screen.getByRole('textbox', { name: 'Image Digest' }), `sha256:${'a'.repeat(64)}`);
    setValue(screen.getByRole('textbox', { name: 'Test Command' }), 'pytest');
    await user.click(screen.getByRole('button', { name: 'Create Runtime' }));

    await waitFor(() => { expect(body).toBeDefined(); });
    expect(body).toMatchObject({ reportFormat: 'JUNIT_XML' });
    expect(REPORT_FORMATS).toContain((body as { reportFormat: string }).reportFormat);
  });

  it('refuses a digest that does not pin the image', async () => {
    stubReads();
    const user = userEvent.setup();
    renderWithProviders(<AdminRuntimesPage />);
    await user.click(await screen.findByRole('button', { name: 'New Runtime' }));

    setValue(screen.getByRole('textbox', { name: 'Key' }), 'python');
    setValue(screen.getByRole('textbox', { name: 'Display Name' }), 'Python');
    setValue(screen.getByRole('textbox', { name: 'Image' }), 'python');
    setValue(screen.getByRole('textbox', { name: 'Tag' }), 'latest');
    setValue(screen.getByRole('textbox', { name: 'Image Digest' }), 'not-a-digest');
    setValue(screen.getByRole('textbox', { name: 'Test Command' }), 'pytest');
    await user.click(screen.getByRole('button', { name: 'Create Runtime' }));

    expect(await screen.findByText("tag 'latest' is not reproducible")).toBeInTheDocument();
    expect(screen.getByText('Must be a valid sha256 digest')).toBeInTheDocument();
  });
});
