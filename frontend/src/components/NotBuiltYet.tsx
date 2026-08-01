// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Box, Paper, Typography } from '@mui/material';
import InfoOutlinedIcon from '@mui/icons-material/InfoOutlined';
import type { ReactNode } from 'react';

interface NotBuiltYetProps {
  readonly title: string;
  readonly explanation: string;
  readonly children?: ReactNode;
}

/**
 * States plainly that a route has no content yet.
 *
 * These routes previously rendered a heading over an empty page, which is
 * indistinguishable from a page whose data failed to load: a user cannot tell whether to
 * wait, retry, or report it. Saying so is not a substitute for building the page, but it
 * does stop the UI from looking broken while it is missing.
 */
export function NotBuiltYet({ title, explanation, children }: NotBuiltYetProps) {
  return (
    <Box>
      <Typography variant="h4" component="h1" gutterBottom>{title}</Typography>
      <Paper
        variant="outlined"
        sx={{ p: 3, display: 'flex', gap: 2, alignItems: 'flex-start', maxWidth: 640 }}
      >
        <InfoOutlinedIcon color="disabled" />
        <Box>
          <Typography gutterBottom>Nothing to show here yet.</Typography>
          <Typography color="text.secondary" variant="body2">{explanation}</Typography>
          {children}
        </Box>
      </Paper>
    </Box>
  );
}
