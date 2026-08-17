// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import 'vitest';
import type { AxeResults } from 'vitest-axe';

declare module 'vitest' {
  interface Assertion<T = any> {
    toHaveNoViolations(): Promise<void>;
  }
}
