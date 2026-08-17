// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterAll, afterEach, beforeAll, describe, expect, it, vi } from 'vitest';
import { http, HttpResponse } from 'msw';
import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { LoginPage } from '../src/pages/LoginPage';
import { MetaProvider } from '../src/components/MetaProvider';
import { meta, renderWithProviders, server } from './harness';

/**
 * Signing in is the only way into the instructor side and had no test at all.
 *
 * The distinction that matters is between a password the server rejected and a request
 * the server refused to read: the second is a stale cross-site request token, and
 * reporting it as bad credentials sends people to reset a password that was fine.
 */
describe('signing in', () => {
  beforeAll(() => { server.listen({ onUnhandledRequest: 'error' }); });
  afterEach(() => { server.resetHandlers(); vi.unstubAllGlobals(); });
  afterAll(() => { server.close(); });

  /**
   * Replaces only the navigation, keeping every other part of the address intact.
   *
   * A bare stub breaks relative request paths, which are resolved against the origin,
   * and the page then fails to load rather than failing to sign in.
   */
  function stubLocation() {
    const assign = vi.fn();
    const current = new URL(window.location.href);
    vi.stubGlobal('location', {
      assign,
      href: current.href,
      origin: current.origin,
      protocol: current.protocol,
      host: current.host,
      hostname: current.hostname,
      port: current.port,
      pathname: '/login',
      search: '',
      hash: '',
      toString: () => current.href
    });
    return assign;
  }

  async function signIn() {
    const user = userEvent.setup();
    await user.type(await screen.findByRole('textbox', { name: 'Username' }), 'teacher');
    await user.type(screen.getByLabelText(/Password/), 'secret');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));
  }

  function renderLogin(state?: unknown) {
    server.use(http.get('/api/v1/meta', () => HttpResponse.json(meta())));
    return renderWithProviders(
      <MetaProvider><LoginPage /></MetaProvider>,
      state === undefined ? { route: '/login' } : { route: '/login', state }
    );
  }

  it('goes to the page the expired session interrupted', async () => {
    const assign = stubLocation();
    server.use(
      http.post('/login', () => new HttpResponse(null, { status: 200 })),
      http.get('/api/v1/me', () => HttpResponse.json({ username: 't', displayName: 'T', actorType: 'HUMAN', roles: ['ROLE_INSTRUCTOR'] }))
    );

    renderLogin({ from: { pathname: '/courses/abc' } });
    await signIn();

    await waitFor(() => { expect(assign).toHaveBeenCalledWith('/courses/abc'); });
  });

  it('refuses a destination that leaves the site', async () => {
    const assign = stubLocation();
    server.use(
      http.post('/login', () => new HttpResponse(null, { status: 200 })),
      http.get('/api/v1/me', () => HttpResponse.json({ username: 't', displayName: 'T', actorType: 'HUMAN', roles: ['ROLE_INSTRUCTOR'] }))
    );

    renderLogin({ from: { pathname: '//elsewhere.example/steal' } });
    await signIn();

    await waitFor(() => { expect(assign).toHaveBeenCalledWith('/'); });
  });

  it('says the credentials were not accepted when they were not', async () => {
    stubLocation();
    server.use(
      http.post('/login', () => new HttpResponse(null, { status: 200 })),
      http.get('/api/v1/me', () => new HttpResponse(null, { status: 401 }))
    );

    renderLogin();
    await signIn();

    expect(await screen.findByText('Those credentials were not accepted.')).toBeInTheDocument();
  });

  it('reports a refused request as a security problem, not a wrong password', async () => {
    stubLocation();
    server.use(http.post('/login', () => new HttpResponse(null, { status: 403 })));

    renderLogin();
    await signIn();

    expect(await screen.findByText(/rejected for security reasons/)).toBeInTheDocument();
    expect(screen.queryByText('Those credentials were not accepted.')).not.toBeInTheDocument();
  });

  it('reports an unreachable service as one', async () => {
    stubLocation();
    server.use(http.post('/login', () => HttpResponse.error()));

    renderLogin();
    await signIn();

    expect(await screen.findByText('The service could not be reached. Try again.')).toBeInTheDocument();
  });

  it('moves the reader to the failure it just reported', async () => {
    stubLocation();
    server.use(http.post('/login', () => new HttpResponse(null, { status: 403 })));

    renderLogin();
    await signIn();

    const alert = await screen.findByRole('alert');
    await waitFor(() => { expect(alert).toHaveFocus(); });
  });
});
