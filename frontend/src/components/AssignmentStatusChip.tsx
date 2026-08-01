// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Chip, Tooltip } from '@mui/material';
import type { ChipProps } from '@mui/material';

interface StatusPresentation {
  readonly label: string;
  readonly color: ChipProps['color'];
  readonly variant?: ChipProps['variant'];
  readonly description: string;
}

/**
 * How each assignment status is shown and what it means.
 *
 * Mirrors `AssignmentStatus` on the server. Whether an assignment currently accepts
 * pushes is the question an instructor scans this column for, so the palette separates
 * the one open state from the four that refuse work.
 */
const PRESENTATION: Record<string, StatusPresentation> = {
  DRAFT: {
    label: 'Draft',
    color: 'default',
    variant: 'outlined',
    description: 'Not published. Students cannot see or push to it.'
  },
  SCHEDULED: {
    label: 'Scheduled',
    color: 'info',
    description: 'Published but not open yet. Pushes are refused until it opens.'
  },
  OPEN: {
    label: 'Open',
    color: 'success',
    description: 'Accepting pushes right now.'
  },
  CLOSED: {
    label: 'Closed',
    color: 'warning',
    description: 'Explicitly closed. Pushes are refused.'
  },
  ARCHIVED: {
    label: 'Archived',
    color: 'default',
    description: 'Kept for history only. Pushes are refused.'
  }
};

interface AssignmentStatusChipProps {
  readonly status: string;
}

/**
 * Shows an assignment status as a labelled, colour-coded chip.
 *
 * An unknown value is shown verbatim so a newer server's status is visible rather than
 * silently blank.
 */
export function AssignmentStatusChip({ status }: AssignmentStatusChipProps) {
  const presentation = PRESENTATION[status];
  if (!presentation) {
    return <Chip size="small" variant="outlined" label={status} />;
  }
  return (
    <Tooltip title={presentation.description}>
      <Chip
        size="small"
        color={presentation.color}
        variant={presentation.variant ?? 'filled'}
        label={presentation.label}
      />
    </Tooltip>
  );
}
