// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

/**
 * Reading a number out of a text field without inventing one.
 *
 * `parseFloat('')` is `NaN`, and the forms then coerced it with `|| 0`, so clearing "Max
 * Points" and pressing create stored an assignment worth zero points without saying so.
 * Undefined is the honest answer for an empty field: it is distinguishable from a
 * deliberate zero, which `|| 0` was not, and the caller decides whether absence is
 * allowed.
 */
export function parseNumberInput(value: string): number | undefined {
  if (value.trim() === '') return undefined;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : undefined;
}

/**
 * Renders a numeric form value for a controlled input, leaving an absent one blank.
 */
export function numberInputValue(value: number | null | undefined): string {
  return value === null || value === undefined || !Number.isFinite(value) ? '' : String(value);
}
