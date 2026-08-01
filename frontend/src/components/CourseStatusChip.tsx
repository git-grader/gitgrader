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
 * How each course status is shown and what it means.
 *
 * Mirrors `CourseStatus` on the server.
 */
const PRESENTATION: Record<string, StatusPresentation> = {
  DRAFT: {
    label: 'Draft',
    color: 'default',
    variant: 'outlined',
    description: 'Still being configured. Not open to students.'
  },
  ACTIVE: {
    label: 'Active',
    color: 'success',
    // Deliberately does not promise registration: that is governed separately by the
    // course's registration flag and its opening window, so an active course can still
    // be closed to new students.
    description: 'Running. Whether registration is open is configured separately.'
  },
  CLOSED: {
    label: 'Closed',
    color: 'warning',
    description: 'Ended but still visible.'
  },
  ARCHIVED: {
    label: 'Archived',
    color: 'default',
    description: 'Retained for history only.'
  }
};

interface CourseStatusChipProps {
  readonly status: string;
}

/**
 * Shows a course status as a labelled, colour-coded chip.
 *
 * An unknown value is shown verbatim so a newer server's status is visible rather than
 * silently blank.
 */
export function CourseStatusChip({ status }: CourseStatusChipProps) {
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
