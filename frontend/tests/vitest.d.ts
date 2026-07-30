import 'vitest';
import type { AxeResults } from 'vitest-axe';

declare module 'vitest' {
  interface Assertion<T = any> {
    toHaveNoViolations(): Promise<void>;
  }
}
