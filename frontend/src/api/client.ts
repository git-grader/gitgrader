// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0



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

function getXsrfToken(): string | null {
  const match = document.cookie.match(new RegExp('(^| )XSRF-TOKEN=([^;]+)'));
  return match ? decodeURIComponent(match[2] || '') : null;
}

/**
 * Submits a form-encoded POST, as Spring Security's sign-in expects.
 *
 * The token is sent as a header rather than a form field on purpose. The server masks
 * the token it renders into forms, so the value readable from the cookie is only
 * accepted from the header; sending it as `_csrf` is rejected.
 */
export async function postForm(path: string, fields: Record<string, string>): Promise<Response> {
  const headers = new Headers({ 'Content-Type': 'application/x-www-form-urlencoded' });
  const xsrf = getXsrfToken();
  if (xsrf) {
    headers.set('X-XSRF-TOKEN', xsrf);
  }
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
 * DO NOT set Content-Type header. Let the browser set it automatically with the boundary.
 */
export async function postMultipart<T>(path: string, formData: FormData): Promise<T> {
  const headers = new Headers();
  headers.set('Accept', 'application/json');
  const xsrf = getXsrfToken();
  if (xsrf) {
    headers.set('X-XSRF-TOKEN', xsrf);
  }

  const res = await fetch(path, {
    method: 'POST',
    headers,
    body: formData,
    redirect: 'follow'
  });

  if (!res.ok) {
    if (res.headers.get('content-type')?.includes('application/problem+json')) {
      const prob = await res.json() as { type: string; title: string; status: number; detail?: string; instance?: string; errors?: { field: string; message: string }[] };
      throw new ApiProblem(prob.type, prob.title, prob.status, prob.detail, prob.instance, prob.errors);
    }
    throw new Error(`API error: ${res.status} ${res.statusText}`);
  }

  if (res.status === 204 || res.status === 202) {
    return {} as T;
  }
  
  if (res.headers.get('content-type')?.includes('application/json')) {
    return await res.json() as T;
  }
  
  return {} as T;
}

export async function fetchApi<T>(path: string, options: RequestInit = {}): Promise<T> {
  const headers = new Headers(options.headers || {});
  if (!headers.has('Accept')) {
    headers.set('Accept', 'application/json');
  }

  const method = options.method?.toUpperCase() || 'GET';
  if (['POST', 'PUT', 'PATCH', 'DELETE'].includes(method)) {
    const xsrf = getXsrfToken();
    if (xsrf) {
      headers.set('X-XSRF-TOKEN', xsrf);
    }
  }

  const res = await fetch(path, { ...options, headers });

  if (!res.ok) {
    if (res.headers.get('content-type')?.includes('application/problem+json')) {
      const prob = await res.json() as { type: string; title: string; status: number; detail?: string; instance?: string; errors?: { field: string; message: string }[] };
      throw new ApiProblem(prob.type, prob.title, prob.status, prob.detail, prob.instance, prob.errors);
    }
    throw new Error(`API error: ${res.status} ${res.statusText}`);
  }

  if (res.status === 204 || res.status === 202) {
    return {} as T;
  }
  
  if (res.headers.get('content-type')?.includes('application/json')) {
    return await res.json() as T;
  }
  
  return {} as T;
}
