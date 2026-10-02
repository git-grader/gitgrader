// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { ApiProblem } from './client';

/** A throttled request must not cause a burst of automatic retries at the proxy. */
export function retryApiQuery(failureCount: number, error: unknown): boolean {
  if (error instanceof ApiProblem && ((error.status >= 400 && error.status < 500) || error.status === 503)) {
    // nginx reports limit_req rejections as 503 by default. A manual retry remains
    // available on the error notice, including when the service is restarting.
    return false;
  }
  return failureCount < 3;
}
