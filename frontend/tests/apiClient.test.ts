// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiProblem, fetchApi, postForm, postMultipart } from '../src/api/client';

/**
 * Covers the one place every call to the API passes through.
 *
 * The CSRF header is the part worth pinning down: it is attached here and nowhere else,
 * so a change that stops sending it would not fail a single component test - every
 * state-changing request would simply start coming back 403 in a running deployment.
 */
describe('api client', () => {
  const fetchMock = vi.fn();

  beforeEach(() => {
    vi.stubGlobal('fetch', fetchMock);
    fetchMock.mockReset();
    clearCookies();
  });

  afterEach(() => {
    vi.unstubAllGlobals();
    clearCookies();
  });

  function clearCookies() {
    for (const entry of document.cookie.split(';')) {
      const name = entry.split('=')[0]?.trim();
      if (name) {
        document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
      }
    }
  }

  function jsonResponse(body: unknown, status = 200) {
    return new Response(JSON.stringify(body), {
      status,
      headers: { 'content-type': 'application/json' }
    });
  }

  function problemResponse(problem: Record<string, unknown>, status: number) {
    return new Response(JSON.stringify(problem), {
      status,
      headers: { 'content-type': 'application/problem+json' }
    });
  }

  function headersOfLastCall(): Headers {
    const init = fetchMock.mock.calls[0]?.[1] as RequestInit;
    return init.headers as Headers;
  }

  describe('cross-site request forgery token', () => {
    it('sends the token from the cookie on a state-changing request', async () => {
      document.cookie = 'XSRF-TOKEN=token-value';
      fetchMock.mockResolvedValue(jsonResponse({ ok: true }));

      await fetchApi('/api/v1/courses', { method: 'POST' });

      expect(headersOfLastCall().get('X-XSRF-TOKEN')).toBe('token-value');
    });

    it.each(['PUT', 'PATCH', 'DELETE'])('sends the token on %s as well', async (method) => {
      document.cookie = 'XSRF-TOKEN=token-value';
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

      await fetchApi('/api/v1/courses/1', { method });

      expect(headersOfLastCall().get('X-XSRF-TOKEN')).toBe('token-value');
    });

    it('does not send the token on a read', async () => {
      document.cookie = 'XSRF-TOKEN=token-value';
      fetchMock.mockResolvedValue(jsonResponse([]));

      await fetchApi('/api/v1/courses');

      expect(headersOfLastCall().get('X-XSRF-TOKEN')).toBeNull();
    });

    it('decodes a token the server percent-encoded', async () => {
      document.cookie = `XSRF-TOKEN=${encodeURIComponent('a+b/c=')}`;
      fetchMock.mockResolvedValue(jsonResponse({}));

      await fetchApi('/api/v1/courses', { method: 'POST' });

      expect(headersOfLastCall().get('X-XSRF-TOKEN')).toBe('a+b/c=');
    });

    it('is not fooled by a different cookie whose name ends in the token name', async () => {
      document.cookie = 'NOT-XSRF-TOKEN=wrong-value';
      fetchMock.mockResolvedValue(jsonResponse({}));

      await fetchApi('/api/v1/courses', { method: 'POST' });

      expect(headersOfLastCall().get('X-XSRF-TOKEN')).toBeNull();
    });

    it('sends the token as a header on a multipart upload', async () => {
      document.cookie = 'XSRF-TOKEN=token-value';
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

      await postMultipart('/api/v1/templates/1/versions', new FormData());

      expect(headersOfLastCall().get('X-XSRF-TOKEN')).toBe('token-value');
    });

    it('leaves the content type to the browser on a multipart upload', async () => {
      // Setting it by hand omits the multipart boundary, and the request is unparseable.
      fetchMock.mockResolvedValue(new Response(null, { status: 204 }));

      await postMultipart('/api/v1/templates/1/versions', new FormData());

      expect(headersOfLastCall().get('Content-Type')).toBeNull();
    });

    it('sends the token as a header on a form post', async () => {
      document.cookie = 'XSRF-TOKEN=token-value';
      fetchMock.mockResolvedValue(new Response(null, { status: 302 }));

      await postForm('/login', { username: 'someone', password: 'secret' });

      expect(headersOfLastCall().get('X-XSRF-TOKEN')).toBe('token-value');
      const body = fetchMock.mock.calls[0]?.[1]?.body as string;
      expect(body).not.toContain('_csrf');
    });
  });

  describe('failures', () => {
    it('raises the problem document the server sent', async () => {
      fetchMock.mockResolvedValue(
        problemResponse(
          {
            type: 'https://example.org/problems/validation',
            title: 'Validation failed',
            status: 400,
            detail: 'Course key is already taken',
            errors: [{ field: 'key', message: 'already taken' }]
          },
          400
        )
      );

      const problem = await fetchApi('/api/v1/courses', { method: 'POST' }).catch((e: unknown) => e);

      expect(problem).toBeInstanceOf(ApiProblem);
      const apiProblem = problem as ApiProblem;
      expect(apiProblem.status).toBe(400);
      expect(apiProblem.title).toBe('Validation failed');
      expect(apiProblem.detail).toBe('Course key is already taken');
      expect(apiProblem.errors).toEqual([{ field: 'key', message: 'already taken' }]);
      expect(apiProblem.message).toBe('Validation failed');
    });

    it('raises a plain error when the failure carries no problem document', async () => {
      fetchMock.mockResolvedValue(new Response('gateway down', { status: 502, statusText: 'Bad Gateway' }));

      await expect(fetchApi('/api/v1/courses')).rejects.toThrow('API error: 502 Bad Gateway');
    });

    it('raises the problem document from a failed upload too', async () => {
      fetchMock.mockResolvedValue(problemResponse({ type: 'about:blank', title: 'Too large', status: 413 }, 413));

      const problem = await postMultipart('/api/v1/templates/1/versions', new FormData()).catch(
        (e: unknown) => e
      );

      expect(problem).toBeInstanceOf(ApiProblem);
      expect((problem as ApiProblem).status).toBe(413);
    });
  });

  describe('successful responses', () => {
    it('returns the parsed body', async () => {
      fetchMock.mockResolvedValue(jsonResponse([{ id: '1' }]));

      await expect(fetchApi('/api/v1/courses')).resolves.toEqual([{ id: '1' }]);
    });

    it.each([204, 202])('returns an empty object for %i, which carries no body', async (status) => {
      fetchMock.mockResolvedValue(new Response(null, { status }));

      await expect(fetchApi('/api/v1/courses/1', { method: 'DELETE' })).resolves.toEqual({});
    });

    it('returns an empty object when the body is not json', async () => {
      fetchMock.mockResolvedValue(new Response('<html></html>', { status: 200, headers: { 'content-type': 'text/html' } }));

      await expect(fetchApi('/api/v1/courses')).resolves.toEqual({});
    });

    it('asks for json unless the caller already said otherwise', async () => {
      fetchMock.mockResolvedValue(jsonResponse({}));

      await fetchApi('/api/v1/courses');

      expect(headersOfLastCall().get('Accept')).toBe('application/json');
    });

    it('keeps an Accept header the caller set', async () => {
      fetchMock.mockResolvedValue(new Response('text', { status: 200, headers: { 'content-type': 'text/plain' } }));

      await fetchApi('/api/v1/courses', { headers: { Accept: 'text/plain' } });

      expect(headersOfLastCall().get('Accept')).toBe('text/plain');
    });
  });
});
