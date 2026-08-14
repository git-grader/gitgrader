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
 * The create dialogs were never affected: they start from an empty string, so the value
 * the user types really is local and converting it once is correct.
 */

const pad = (value: number) => String(value).padStart(2, '0');

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
