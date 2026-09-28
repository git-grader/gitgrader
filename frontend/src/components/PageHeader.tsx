// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import type { ReactNode } from 'react';
import { Box, Typography } from '@mui/material';

interface PageHeaderProps {
  readonly title: string;
  readonly description?: string;
  readonly actions?: ReactNode;
}

export function PageHeader({ title, description, actions }: PageHeaderProps) {
  return (
    <Box
      component="header"
      sx={{
        display: 'flex',
        alignItems: { xs: 'flex-start', md: 'center' },
        justifyContent: 'space-between',
        flexWrap: 'wrap',
        gap: 2
      }}
    >
      <Box sx={{ minWidth: 0, flex: '1 1 220px' }}>
        <Typography variant="h4" component="h1" sx={{ lineHeight: 1.2, overflowWrap: 'anywhere', mb: description ? 0.5 : 0 }}>
          {title}
        </Typography>
        {description && (
          <Typography variant="body2" color="text.secondary" sx={{ maxWidth: '72ch' }}>
            {description}
          </Typography>
        )}
      </Box>
      {actions && (
        <Box
          sx={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: { xs: 'flex-start', sm: 'flex-end' },
            flexWrap: 'wrap',
            gap: 1.5,
            width: { xs: '100%', sm: 'auto' },
            minWidth: 0
          }}
        >
          {actions}
        </Box>
      )}
    </Box>
  );
}