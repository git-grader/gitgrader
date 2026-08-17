// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { describe, expect, it } from 'vitest';
import {
  fromLocalInputValue,
  fromZonedInputValue,
  toLocalInputValue,
  toZonedInputValue
} from '../src/components/localDateTime';

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

  /**
   * A course and an assignment carry a timezone of their own, and it is the one the
   * deadline means. Converting through the browser's instead made an instructor in
   * Vienna scheduling a New York assignment for 23:59 store 23:59 Vienna - six hours
   * early for every student it applied to.
   *
   * These run in Europe/Zurich, so a New York assignment is a real disagreement.
   */
  describe('in a stated timezone', () => {
    it('reads a wall-clock value as the instant it names there', () => {
      expect(fromZonedInputValue('2026-03-01T23:59', 'America/New_York')).toBe('2026-03-02T04:59:00.000Z');
    });

    it('does not use the reader\'s zone when a zone was given', () => {
      expect(fromZonedInputValue('2026-03-01T23:59', 'America/New_York'))
        .not.toBe(fromLocalInputValue('2026-03-01T23:59'));
    });

    it('renders an instant as the wall-clock time it is there', () => {
      expect(toZonedInputValue('2026-03-02T04:59:00Z', 'America/New_York')).toBe('2026-03-01T23:59');
    });

    it.each([
      ['2026-01-15T09:30:00Z', 'America/New_York'],
      ['2026-07-15T09:30:00Z', 'America/New_York'],
      ['2026-03-29T00:30:00Z', 'Europe/Zurich'],
      ['2026-11-01T05:30:00Z', 'America/New_York'],
      ['2026-06-01T12:00:00Z', 'Asia/Kolkata'],
      ['2026-06-01T12:00:00Z', 'UTC']
    ])('round-trips %s in %s unchanged', (iso, zone) => {
      expect(fromZonedInputValue(toZonedInputValue(iso, zone), zone)).toBe(new Date(iso).toISOString());
    });

    it('survives repeated save cycles without drifting', () => {
      const original = '2026-03-01T23:59:00.000Z';
      let value = toZonedInputValue(original, 'America/New_York');
      for (let i = 0; i < 5; i++) {
        value = toZonedInputValue(fromZonedInputValue(value, 'America/New_York'), 'America/New_York');
      }
      expect(fromZonedInputValue(value, 'America/New_York')).toBe(original);
    });

    /**
     * The zone is typed by hand on the course and assignment forms, so an unusable one is
     * an ordinary input mistake. It must not throw: this runs inside a submit handler,
     * and `new Date('nonsense').toISOString()` raises, which took the filled-in form with
     * it.
     */
    it.each(['Not/AZone', '', 'Europe/Nowhere'])('falls back rather than throwing for %p', zone => {
      expect(() => fromZonedInputValue('2026-03-01T23:59', zone)).not.toThrow();
      expect(fromZonedInputValue('2026-03-01T23:59', zone)).toBe(fromLocalInputValue('2026-03-01T23:59'));
      expect(() => toZonedInputValue('2026-03-01T23:59:00Z', zone)).not.toThrow();
    });

    it.each([null, undefined, '', 'not-a-date'])('treats %p as no value', input => {
      expect(fromZonedInputValue(input, 'America/New_York')).toBeNull();
      expect(toZonedInputValue(input, 'America/New_York')).toBe('');
    });
  });
});
