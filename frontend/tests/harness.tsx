// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import type { ReactElement } from 'react';
import { render } from '@testing-library/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { MemoryRouter } from 'react-router';
import { setupServer } from 'msw/node';
import { axe } from 'vitest-axe';
import { expect } from 'vitest';

/**
 * One request layer for the tests that exercise a real page.
 *
 * Mocking the `api` module tests everything except the module where the contract lives:
 * a schema that disagrees with the server, a request body the server would reject, or a
 * cache key that no longer matches all survive a mocked call. These tests answer real
 * HTTP instead, so the fetch, the parse and the query key are all in the loop.
 */
export const server = setupServer();

export function createTestQueryClient(): QueryClient {
  return new QueryClient({
    defaultOptions: { queries: { retry: false }, mutations: { retry: false } }
  });
}

interface RenderOptions {
  readonly queryClient?: QueryClient;
  readonly route?: string;
  readonly state?: unknown;
}

export function renderWithProviders(ui: ReactElement, options: RenderOptions = {}) {
  const queryClient = options.queryClient ?? createTestQueryClient();
  const entry = options.state === undefined
    ? (options.route ?? '/')
    : { pathname: options.route ?? '/', state: options.state };

  return {
    queryClient,
    ...render(
      <QueryClientProvider client={queryClient}>
        <MemoryRouter initialEntries={[entry]}>{ui}</MemoryRouter>
      </QueryClientProvider>
    )
  };
}

/**
 * Fails with the rule identifiers rather than a wall of serialised nodes.
 *
 * Replaces the `toHaveNoViolations` matcher, whose type declaration had to restate a
 * vitest interface to exist at all and reported the promise it returned as unhandled.
 */
export async function expectNoAxeViolations(container: Element): Promise<void> {
  const results = await axe(container);
  expect(results.violations.map((violation) => `${violation.id}: ${violation.help}`)).toEqual([]);
}

export function meta(overrides: Record<string, unknown> = {}) {
  return {
    name: 'Test Instance',
    organizationName: 'Test Org',
    supportEmail: 'support@example.org',
    documentationUrl: 'https://example.org/docs',
    publicUrl: 'https://example.org',
    sshHost: 'ssh.example.org',
    sshPort: 2222,
    registrationEnabled: true,
    version: '1.0.0',
    ...overrides
  };
}

export function page(content: unknown[]) {
  return { content, totalElements: content.length, totalPages: 1, size: 20, number: 0 };
}

export function problem(status: number, body: Record<string, unknown>) {
  return new Response(JSON.stringify({ type: 'about:blank', status, ...body }), {
    status,
    headers: { 'content-type': 'application/problem+json' }
  });
}
