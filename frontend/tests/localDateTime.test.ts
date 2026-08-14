// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from 'vitest';
import { fromLocalInputValue, toLocalInputValue } from '../src/components/localDateTime';

/**
 * The edit forms seed a `datetime-local` control from an instant and read it back on
 * save. Getting one half right and the other wrong is silent: the control shows a
 * plausible time either way, and the damage only appears in the stored deadline.
 *
 * These run under a fixed TZ (see the `test.env` block in vite.config.ts) so that the
 * offset is real and constant rather than whatever the machine happens to use.
 */
describe('localDateTime', () => {
  it('renders a UTC instant as local wall-clock time', () => {
    // Europe/Zurich is UTC+1 in January.
    expect(toLocalInputValue('2026-01-15T09:30:00Z')).toBe('2026-01-15T10:30');
  });

  it('renders a summer instant with the daylight-saving offset', () => {
    // Europe/Zurich is UTC+2 in July.
    expect(toLocalInputValue('2026-07-15T09:30:00Z')).toBe('2026-07-15T11:30');
  });

  it('reads a local wall-clock value back as the same instant', () => {
    expect(fromLocalInputValue('2026-01-15T10:30')).toBe('2026-01-15T09:30:00.000Z');
  });

  /**
   * The regression that mattered: opening a form and saving it untouched must not move
   * the deadline. Slicing the instant and re-parsing it converted the offset twice, so
   * every save shifted the value by one offset and cut students off early.
   */
  it.each([
    '2026-01-15T09:30:00Z',
    '2026-07-15T09:30:00Z',
    '2026-03-29T00:30:00Z',
    '2026-12-31T23:00:00Z'
  ])('round-trips %s unchanged', iso => {
    const seeded = toLocalInputValue(iso);
    expect(fromLocalInputValue(seeded)).toBe(new Date(iso).toISOString());
  });

  it('survives repeated save cycles without drifting', () => {
    const original = '2026-01-15T09:30:00Z';
    let value = toLocalInputValue(original);
    for (let i = 0; i < 5; i++) {
      value = toLocalInputValue(fromLocalInputValue(value));
    }
    expect(fromLocalInputValue(value)).toBe(new Date(original).toISOString());
  });

  it.each([null, undefined, ''])('treats %p as no value', input => {
    expect(toLocalInputValue(input)).toBe('');
    expect(fromLocalInputValue(input)).toBeNull();
  });

  it('treats an unparseable value as no value', () => {
    expect(toLocalInputValue('not-a-date')).toBe('');
    expect(fromLocalInputValue('not-a-date')).toBeNull();
  });
});
