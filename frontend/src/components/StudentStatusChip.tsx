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
 * How each student status is shown and what it means.
 *
 * Mirrors `StudentStatus` on the server. The stored values are machine constants, and
 * rendering them raw put `VERIFIED_BY_INS...` in the grid, which is both unreadable and
 * ambiguous about whether the student may still submit.
 */
const PRESENTATION: Record<string, StatusPresentation> = {
  SELF_REGISTERED: {
    label: 'Self-registered',
    color: 'default',
    variant: 'outlined',
    description: 'Registered through the public form but not yet verified by an instructor.'
  },
  VERIFIED_BY_INSTRUCTOR: {
    label: 'Verified',
    color: 'success',
    description: 'An instructor confirmed this student’s identity.'
  },
  SUSPENDED: {
    label: 'Suspended',
    color: 'warning',
    description: 'Temporarily prevented from submitting. Pushes are refused.'
  },
  ARCHIVED: {
    label: 'Archived',
    color: 'default',
    description: 'Kept as historical data at the end of a course. Cannot submit.'
  }
};

interface StudentStatusChipProps {
  readonly status: string;
}

/**
 * Shows a student status as a labelled, colour-coded chip.
 *
 * An unknown value is shown verbatim so a newer server's status is visible rather than
 * silently blank.
 */
export function StudentStatusChip({ status }: StudentStatusChipProps) {
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
