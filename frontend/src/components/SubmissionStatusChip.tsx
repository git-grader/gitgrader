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
 * How each submission status is shown and what it means.
 *
 * Mirrors `SubmissionStatus` on the server. The stored values are machine constants
 * and were previously rendered raw, so an instructor read `INFRASTRUCTURE_ERROR`
 * truncated to `INFRASTRUCTUR...` and had no way to tell a platform fault from a
 * failed test.
 */
const PRESENTATION: Record<string, StatusPresentation> = {
  RECEIVED: {
    label: 'Received',
    color: 'default',
    variant: 'outlined',
    description: 'The push was recorded. Grading has not been scheduled yet.'
  },
  QUEUED: {
    label: 'Queued',
    color: 'info',
    description: 'Waiting for a free grading worker.'
  },
  RUNNING: {
    label: 'Running',
    color: 'info',
    description: 'Executing in a sandbox right now.'
  },
  PASSED: {
    label: 'Passed',
    color: 'success',
    description: 'Graded, and the score met the assignment’s pass threshold.'
  },
  FAILED: {
    label: 'Failed',
    color: 'error',
    description: 'Graded, and the score did not meet the pass threshold.'
  },
  INFRASTRUCTURE_ERROR: {
    label: 'Platform error',
    color: 'warning',
    description: 'Grading could not be carried out. This is not the student’s fault and can be retried.'
  },
  CANCELLED: {
    label: 'Cancelled',
    color: 'default',
    // Deliberately not labelled "Superseded". The server sets CANCELLED both when a
    // newer push replaces an unstarted run and when a queue ceiling refuses to schedule
    // one, and the submission carries nothing that tells the two apart. Naming only the
    // first cause would state the wrong reason to the student whose work hit a ceiling;
    // the audit trail records which decision it actually was.
    description:
      'Recorded but not graded, either because a newer push replaced it before it started or because a queue limit was reached. The attempt is still kept.'
  },
  REJECTED: {
    label: 'Rejected',
    color: 'error',
    description: 'The push was refused, so nothing was graded. The reason is recorded on the submission.'
  }
};

interface SubmissionStatusChipProps {
  readonly status: string;
}

/**
 * Shows a submission status as a labelled, colour-coded chip.
 *
 * An unknown value is shown verbatim rather than hidden: a status this build does not
 * know about is worth seeing, and silently dropping it would make a newer server look
 * like it returned nothing.
 */
export function SubmissionStatusChip({ status }: SubmissionStatusChipProps) {
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
