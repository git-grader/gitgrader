// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Route, Routes } from 'react-router';
import { InstructorLayout } from '../src/components/InstructorLayout';
import { RequireAdmin } from '../src/components/RequireAdmin';
import { MetaProvider } from '../src/components/MetaProvider';
import { meta, renderWithProviders, server } from './harness';

const INSTRUCTOR = { username: 't', displayName: 'Terry Teacher', actorType: 'HUMAN', roles: ['ROLE_INSTRUCTOR'] };
const ADMIN = { username: 'a', displayName: 'Ada Admin', actorType: 'HUMAN', roles: ['ROLE_INSTRUCTOR', 'ROLE_ADMIN'] };

function problemResponse(status: number) {
  return new HttpResponse(JSON.stringify({ type: 'about:blank', title: 'Nope', status }), {
    status,
    headers: { 'content-type': 'application/problem+json' }
  });
}

describe('the instructor shell', () => {
  beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
  afterEach(() => { server.resetHandlers(); vi.unstubAllGlobals(); });
  afterAll(() => { server.close(); });

  function renderShell() {
    server.use(http.get('/api/v1/meta', () => HttpResponse.json(meta())));
    return renderWithProviders(
      <MetaProvider>
        <Routes>
          <Route path="/dashboard" element={<InstructorLayout />} />
          <Route path="/login" element={<p>Sign-in page</p>} />
        </Routes>
      </MetaProvider>,
      { route: '/dashboard' }
    );
  }

  /**
   * Every failure used to send the user to sign in again, so a dropped connection or a
   * 500 discarded whatever they had open. Only a refusal means the session is over.
   */
  it('sends an expired session back to sign in', async () => {
    server.use(http.get('/api/v1/me', () => problemResponse(401)));
    renderShell();
    expect(await screen.findByText('Sign-in page')).toBeInTheDocument();
  });

  it.each([500, 503])('keeps the user where they are when the server answers %i', async (status) => {
    server.use(http.get('/api/v1/me', () => problemResponse(status)));
    renderShell();

    expect(await screen.findByText('GitGrader could not be reached.')).toBeInTheDocument();
    expect(screen.queryByText('Sign-in page')).not.toBeInTheDocument();
  });

  /**
   * The cache holds student records and audit entries. Signing out with a router push
   * left all of it in memory for whoever signed in next in the same document.
   */
  it('takes the previous user\'s data with it when signing out', async () => {
    const assign = vi.fn();
    const current = new URL(window.location.href);
    vi.stubGlobal('location', {
      assign, href: current.href, origin: current.origin, protocol: current.protocol,
      host: current.host, hostname: current.hostname, port: current.port,
      pathname: '/dashboard', search: '', hash: '', toString: () => current.href
    });
    server.use(
      http.get('/api/v1/me', () => HttpResponse.json(INSTRUCTOR)),
      http.post('/logout', () => new HttpResponse(null, { status: 204 }))
    );

    const { queryClient } = renderShell();
    await screen.findByRole('button', { name: 'Sign out' });
    expect(queryClient.getQueryCache().getAll().length).toBeGreaterThan(0);

    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign out' }));

    await waitFor(() => { expect(queryClient.getQueryCache().getAll()).toEqual([]); });
    expect(assign).toHaveBeenCalledWith('/login');
  });

  it('still signs out when the server refuses the request', async () => {
    const assign = vi.fn();
    const current = new URL(window.location.href);
    vi.stubGlobal('location', {
      assign, href: current.href, origin: current.origin, protocol: current.protocol,
      host: current.host, hostname: current.hostname, port: current.port,
      pathname: '/dashboard', search: '', hash: '', toString: () => current.href
    });
    server.use(
      http.get('/api/v1/me', () => HttpResponse.json(INSTRUCTOR)),
      http.post('/logout', () => problemResponse(403))
    );

    renderShell();
    await userEvent.setup().click(await screen.findByRole('button', { name: 'Sign out' }));

    await waitFor(() => { expect(assign).toHaveBeenCalledWith('/login'); });
  });

  it('offers a way past the navigation to the content', async () => {
    server.use(http.get('/api/v1/me', () => HttpResponse.json(INSTRUCTOR)));
    renderShell();

    const skip = await screen.findByRole('link', { name: 'Skip to main content' });
    await userEvent.setup().click(skip);
    expect(document.getElementById('main-content')).toHaveFocus();
  });
});

/**
 * Hiding the navigation links was the whole of the check, so an instructor who typed the
 * address got the page, a 403 from the server, and a message saying the audit log could
 * not be loaded - which reads as an outage rather than a refusal.
 */
describe('the administrator pages', () => {
  beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
  afterEach(() => { server.resetHandlers(); });
  afterAll(() => { server.close(); });

  function renderGuard() {
    return renderWithProviders(
      <Routes>
        <Route path="/admin" element={<RequireAdmin />}>
          <Route path="audit" element={<p>Audit log</p>} />
        </Route>
      </Routes>,
      { route: '/admin/audit' }
    );
  }

  it('refuses an instructor by name rather than by failure', async () => {
    server.use(http.get('/api/v1/me', () => HttpResponse.json(INSTRUCTOR)));
    renderGuard();

    expect(await screen.findByText('Administrators only')).toBeInTheDocument();
    expect(screen.getByText(/Terry Teacher/)).toBeInTheDocument();
    expect(screen.queryByText('Audit log')).not.toBeInTheDocument();
  });

  it('lets an administrator through', async () => {
    server.use(http.get('/api/v1/me', () => HttpResponse.json(ADMIN)));
    renderGuard();

    expect(await screen.findByText('Audit log')).toBeInTheDocument();
  });
});
