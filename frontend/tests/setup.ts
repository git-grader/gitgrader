// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import '@testing-library/jest-dom/vitest';
import { afterAll, afterEach, beforeAll, beforeEach } from 'vitest';
import { configure } from '@testing-library/react';
import { setupServer } from 'msw/node';

/**
 * The one MSW server every test file shares.
 *
 * The lifecycle lives here rather than in each test file so that a new test gets it by
 * default instead of by remembering three lines. `onUnhandledFrame: 'error'` is the point
 * of the arrangement: a request no handler covers is a test that would otherwise pass
 * while asserting nothing about the response it received.
 */
export const server = setupServer();

beforeAll(() => { server.listen({ onUnhandledFrame: 'error' }); });
afterEach(() => { server.resetHandlers(); });
afterAll(() => { server.close(); });

// findBy* waits one second by default, which is a statement about a fast machine rather
// than about this suite: under coverage instrumentation on a loaded runner a render that
// was merely slow got reported as an assertion that never came true.
configure({ asyncUtilTimeout: 5_000 });

// Each test starts as a fresh browser; persistence is tested within individual cases.
beforeEach(() => { localStorage.clear(); });
