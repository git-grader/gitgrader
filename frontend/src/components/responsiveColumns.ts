// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useTheme, useMediaQuery } from '@mui/material';

/**
 * Reports whether the viewport is narrower than the desktop breakpoint.
 *
 * Shared so that every grid agrees on where "narrow" begins; a page that picked its own
 * breakpoint would switch layout at a different width from the navigation drawer.
 * @returns true below the md breakpoint
 */
export function useIsNarrow(): boolean {
  const theme = useTheme();
  return useMediaQuery(theme.breakpoints.down('md'));
}

/**
 * Hides a grid's lower-priority columns when the viewport is too narrow for them.
 *
 * Only suitable when the hidden fields are genuinely secondary. Where every column
 * matters and no detail page exists to fall back on, render one stacked composite cell
 * instead - hiding data the user cannot reach anywhere else is worse than scrolling.
 *
 * Fields absent from a `columnVisibilityModel` stay visible, so the narrow model has to
 * name every field explicitly rather than only the ones being hidden.
 *
 * @param all every field the grid defines
 * @param essential the fields worth keeping when space is short
 * @returns a DataGrid `columnVisibilityModel`, empty at desktop widths
 */
export function useNarrowColumns(
  all: readonly string[],
  essential: readonly string[]
): Record<string, boolean> {
  const isNarrow = useIsNarrow();
  if (!isNarrow) return {};
  return Object.fromEntries(all.map((field) => [field, essential.includes(field)]));
}
