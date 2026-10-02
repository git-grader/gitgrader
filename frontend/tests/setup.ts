// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import '@testing-library/jest-dom/vitest';
import { beforeEach } from 'vitest';
import { configure } from '@testing-library/react';

// findBy* waits one second by default, which is a statement about a fast machine rather
// than about this suite: under coverage instrumentation on a loaded runner a render that
// was merely slow got reported as an assertion that never came true.
configure({ asyncUtilTimeout: 5_000 });

// Each test starts as a fresh browser; persistence is tested within individual cases.
beforeEach(() => { localStorage.clear(); });
