// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { Box, Button, TextField, Typography, Paper } from '@mui/material';

export function LoginPage() {
  return (
    <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '100vh' }}>
      <Paper sx={{ p: 4, width: 400 }}>
        <Typography variant="h5" gutterBottom>Login</Typography>
        <form method="POST" action="/api/v1/auth/login">
          <TextField fullWidth margin="normal" label="Username" name="username" required />
          <TextField fullWidth margin="normal" label="Password" name="password" type="password" required />
          <Button type="submit" variant="contained" color="primary" fullWidth sx={{ mt: 2 }}>Log In</Button>
        </form>
      </Paper>
    </Box>
  );
}
