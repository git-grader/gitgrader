// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Alert, Box, Button } from '@mui/material';

/**
 * What a page shows when the request behind it failed.
 *
 * A failed query leaves its data undefined, which is indistinguishable from a collection
 * that is genuinely empty. Pages rendering that state directly reported no courses, no
 * submissions, or an assignment that did not exist, when in fact nothing had been asked
 * of the server successfully - and offered no way to try again. This mirrors the notice
 * MetaProvider already shows, so a failure looks the same wherever it happens.
 */
export function QueryErrorNotice({ message, onRetry }: { message: string; onRetry: () => void }) {
  return (
    <Box sx={{ p: 2 }}>
      <Alert
        severity="error"
        action={
          <Button color="inherit" size="small" onClick={onRetry}>
            Retry
          </Button>
        }
      >
        {message}
      </Alert>
    </Box>
  );
}
