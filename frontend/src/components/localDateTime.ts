// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

/**
 * Conversions between an instant from the API and an `<input type="datetime-local">`.
 *
 * The API speaks UTC instants (`2026-03-01T09:00:00Z`); the control speaks local wall
 * clock with no zone at all (`2026-03-01T09:00`). Both look alike enough that slicing the
 * first sixteen characters off the instant appears to work, and that is the bug these
 * functions exist to prevent: slicing hands the control UTC digits which it then displays
 * as local time, and `new Date` parses a zone-less date-time as local per ECMA-262, so
 * saving converts those same digits *again*. An instructor in UTC+1 who opened an
 * assignment and pressed save without touching a field moved its deadline back an hour,
 * and every further save moved it again. Because `dueAt` decides late submissions, that
 * silently cut students off early.
 *
 * A course and an assignment also carry a timezone of their own, which is the one the
 * deadline means. Converting through the browser's zone instead made an instructor in
 * Vienna scheduling a New York assignment for 23:59 store 23:59 Vienna - six hours early
 * for every student it applied to. The zoned pair below is what the forms use; the plain
 * pair remains for values whose only zone is the reader's.
 */

const pad = (value: number) => String(value).padStart(2, '0');

const WALL_CLOCK = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/;

/**
 * Renders a UTC instant as the local wall-clock value the control expects.
 */
export function toLocalInputValue(iso: string | null | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}`
  );
}

/**
 * Reads the control's local wall-clock value back as a UTC instant.
 */
export function fromLocalInputValue(value: string | null | undefined): string | null {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}

/**
 * How far ahead of UTC the zone is at that instant, in milliseconds.
 *
 * Returns null for a zone the platform does not know. The timezone is typed by hand on
 * the course and assignment forms, so an unrecognised one is an ordinary input mistake
 * rather than a reason to throw out of a submit handler.
 */
function zoneOffsetMs(instantMs: number, timeZone: string): number | null {
  if (!Number.isFinite(instantMs)) return null;
  let parts: Intl.DateTimeFormatPart[];
  try {
    parts = new Intl.DateTimeFormat('en-US', {
      timeZone,
      hourCycle: 'h23',
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      second: '2-digit'
    }).formatToParts(new Date(instantMs));
  }
  catch {
    return null;
  }

  const read = (type: Intl.DateTimeFormatPartTypes): number => {
    const part = parts.find((candidate) => candidate.type === type);
    return part ? Number(part.value) : Number.NaN;
  };

  const asUtc = Date.UTC(read('year'), read('month') - 1, read('day'), read('hour'), read('minute'), read('second'));
  return Number.isNaN(asUtc) ? null : asUtc - instantMs;
}

/**
 * Renders a UTC instant as the wall-clock time it is in the given zone.
 *
 * Falls back to the reader's own zone when the zone is unusable, which is what the
 * control showed before a zone was consulted at all.
 */
export function toZonedInputValue(iso: string | null | undefined, timeZone: string | null | undefined): string {
  if (!iso) return '';
  const date = new Date(iso);
  if (Number.isNaN(date.getTime())) return '';
  if (!timeZone) return toLocalInputValue(iso);

  const offset = zoneOffsetMs(date.getTime(), timeZone);
  if (offset === null) return toLocalInputValue(iso);

  const shifted = new Date(date.getTime() + offset);
  return (
    `${shifted.getUTCFullYear()}-${pad(shifted.getUTCMonth() + 1)}-${pad(shifted.getUTCDate())}` +
    `T${pad(shifted.getUTCHours())}:${pad(shifted.getUTCMinutes())}`
  );
}

/**
 * Reads the control's wall-clock value back as the UTC instant it names in that zone.
 *
 * Returns null rather than throwing for anything unparseable: this runs inside a submit
 * handler, and `new Date('nonsense').toISOString()` raises, which used to take the whole
 * filled-in form down with it.
 */
export function fromZonedInputValue(
  value: string | null | undefined,
  timeZone: string | null | undefined
): string | null {
  if (!value) return null;
  const match = WALL_CLOCK.exec(value);
  if (!match) return null;

  const [, year, month, day, hour, minute] = match;
  if (!year || !month || !day || !hour || !minute) return null;

  const wallAsUtc = Date.UTC(Number(year), Number(month) - 1, Number(day), Number(hour), Number(minute));
  if (Number.isNaN(wallAsUtc)) return null;
  if (!timeZone) return fromLocalInputValue(value);

  const guess = zoneOffsetMs(wallAsUtc, timeZone);
  if (guess === null) return fromLocalInputValue(value);

  let instant = wallAsUtc - guess;
  // One refinement, because the first offset was read at the wrong instant and a
  // daylight-saving change inside that window would otherwise land an hour out.
  const refined = zoneOffsetMs(instant, timeZone);
  if (refined !== null && refined !== guess) {
    instant = wallAsUtc - refined;
  }

  const date = new Date(instant);
  return Number.isNaN(date.getTime()) ? null : date.toISOString();
}
