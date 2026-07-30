// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, TextField, Typography, Paper, useTheme } from '@mui/material';
import { useMeta } from '../components/MetaProvider';
import PrimaryLogo from '../assets/brand/gitgrader-lockup-primary.svg';
import ReversedLogo from '../assets/brand/gitgrader-lockup-reversed.svg';

export function LoginPage() {
  const theme = useTheme();
  const meta = useMeta();

  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh', bgcolor: 'background.default' }}>
      <Paper sx={{ p: 4, width: 400, display: 'flex', flexDirection: 'column', alignItems: 'center' }} elevation={3}>
        <Box component="img" src={theme.palette.mode === 'dark' ? ReversedLogo : PrimaryLogo} alt="GitGrader" sx={{ height: 48, mb: 3 }} />
        {meta.organizationName && (
          <Typography variant="subtitle1" color="text.secondary" gutterBottom>
            {meta.organizationName}
          </Typography>
        )}
        <form method="POST" action="/api/v1/auth/login" style={{ width: '100%' }}>
          <TextField fullWidth margin="normal" label="Username" name="username" required />
          <TextField fullWidth margin="normal" label="Password" name="password" type="password" required />
          <Button type="submit" variant="contained" color="primary" fullWidth sx={{ mt: 2 }}>Log In</Button>
        </form>
      </Paper>
    </Box>
  );
}
