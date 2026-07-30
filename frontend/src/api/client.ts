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
