// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Box, useTheme } from '@mui/material';
import PrimaryLogo from '../assets/brand/gitgrader-lockup-primary.svg';
import ReversedLogo from '../assets/brand/gitgrader-lockup-reversed.svg';

/**
 * The product lockup, for the pages a student reaches without signing in.
 *
 * The reversed artwork is used on dark surfaces, as the brand requires, and the
 * lockup is never drawn below the 48px it is specified for.
 *
 * The alt text names the artwork rather than the configured instance name: the lockup
 * is the product's own wordmark, and an operator who renames their instance has not
 * renamed it. That also keeps this free of the meta context, so it renders anywhere.
 */
export function BrandMark() {
  const theme = useTheme();
  return (
    <Box
      component="img"
      src={theme.palette.mode === 'dark' ? ReversedLogo : PrimaryLogo}
      alt="GitGrader"
      sx={{ height: 48, mb: 3, display: 'block' }}
    />
  );
}
