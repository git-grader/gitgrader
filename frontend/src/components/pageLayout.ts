// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

/** Fill the outlet while allowing long pages to scroll in the main content area. */
export const tablePageSx = {
  display: 'flex',
  flexDirection: 'column',
  flex: '1 0 auto',
  minWidth: 0,
  gap: 3,
  // Filters, notices and summaries must keep their natural height.
  '& > *': { flexShrink: 0 }
} as const;

/** Grow with the viewport; retain room for rows when controls need more space. */
export const tablePanelSx = {
  flex: { xs: '1 0 520px', md: '1 0 360px' },
  minHeight: { xs: 520, md: 360 },
  width: '100%',
  minWidth: 0,
  display: 'flex',
  flexDirection: 'column',
  overflow: 'hidden',
  '& > .MuiDataGrid-root': { flex: '1 1 0', minHeight: 0 }
} as const;
