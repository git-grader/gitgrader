// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

/** RFC 9457 problem document, as every endpoint reports a failure. */
interface ProblemDocument {
  type: string;
  title: string;
  status: number;
  detail?: string;
  instance?: string;
  errors?: { field: string; message: string }[];
}

export class ApiProblem extends Error {
  constructor(
    public readonly type: string,
    public readonly title: string,
    public readonly status: number,
    public readonly detail?: string,
    public readonly instance?: string,
    public readonly errors?: { field: string; message: string }[]
  ) {
    super(title);
    this.name = 'ApiProblem';
  }
}

const XSRF_COOKIE = /(^| )XSRF-TOKEN=([^;]+)/;

const METHODS_NEEDING_CSRF = new Set(['POST', 'PUT', 'PATCH', 'DELETE']);

function getXsrfToken(): string | null {
  const match = document.cookie.match(XSRF_COOKIE);
  return match ? decodeURIComponent(match[2] || '') : null;
}

/**
 * Adds the token as a header rather than a form field on purpose. The server masks the
 * token it renders into forms, so the value readable from the cookie is only accepted
 * from the header; sending it as `_csrf` is rejected.
 */
function withCsrfToken(headers: Headers): Headers {
  const xsrf = getXsrfToken();
  if (xsrf) {
    headers.set('X-XSRF-TOKEN', xsrf);
  }
  return headers;
}

async function failureOf(res: Response): Promise<Error> {
  if (res.headers.get('content-type')?.includes('application/problem+json')) {
    const problem = (await res.json()) as ProblemDocument;
    return new ApiProblem(
      problem.type,
      problem.title,
      problem.status,
      problem.detail,
      problem.instance,
      problem.errors
    );
  }
  return new Error(`API error: ${res.status} ${res.statusText}`);
}

async function readBody<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw await failureOf(res);
  }
  // 202 and 204 are answers in themselves; so is anything that is not json, which is
  // what a redirect to the sign-in page looks like by the time it arrives here.
  if (res.status === 204 || res.status === 202) {
    return {} as T;
  }
  if (res.headers.get('content-type')?.includes('application/json')) {
    return (await res.json()) as T;
  }
  return {} as T;
}

/**
 * Submits a form-encoded POST, as Spring Security's sign-in expects.
 *
 * Returns the raw response: a rejected sign-in still answers with a redirect, so the
 * status does not say whether it worked and the caller has to ask separately.
 */
export async function postForm(path: string, fields: Record<string, string>): Promise<Response> {
  const headers = withCsrfToken(new Headers({ 'Content-Type': 'application/x-www-form-urlencoded' }));
  return fetch(path, {
    method: 'POST',
    headers,
    body: new URLSearchParams(fields).toString(),
    redirect: 'follow'
  });
}

/**
 * Submits a multipart/form-data POST.
 *
 * The content type is deliberately left unset, because only the browser can add the
 * boundary that makes the body parseable.
 */
export async function postMultipart<T>(path: string, formData: FormData): Promise<T> {
  const headers = withCsrfToken(new Headers({ Accept: 'application/json' }));
  const res = await fetch(path, { method: 'POST', headers, body: formData, redirect: 'follow' });
  return readBody<T>(res);
}

export async function fetchApi<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }
  if (METHODS_NEEDING_CSRF.has(options.method?.toUpperCase() || 'GET')) {
    withCsrfToken(headers);
  }

  const res = await fetch(path, { ...options, headers });
  return readBody<T>(res);
}
