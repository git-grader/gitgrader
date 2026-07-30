// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { Alert, Box, Button, CircularProgress, Paper, TextField, Typography, useTheme } from '@mui/material';
import { useMeta } from '../components/MetaProvider';
import { postForm } from '../api/client';
import PrimaryLogo from '../assets/brand/gitgrader-lockup-primary.svg';
import ReversedLogo from '../assets/brand/gitgrader-lockup-reversed.svg';

/** Reads a text field, ignoring anything a file input could have put there. */
function text(form: FormData, field: string): string {
  const value = form.get(field);
  return typeof value === 'string' ? value : '';
}

export function LoginPage() {
  const theme = useTheme();
  const meta = useMeta();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      await postForm('/login', { username: text(form, 'username'), password: text(form, 'password') });
      // A rejected sign-in still answers with a redirect, so the response alone does
      // not say whether it worked. Asking who we are now does.
      const me = await fetch('/api/v1/me', { headers: { Accept: 'application/json' } });
      if (!me.ok) {
        setError('Those credentials were not accepted.');
        return;
      }
      window.location.assign('/');
    } catch {
      setError('The service could not be reached. Try again.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Box
      sx={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '100vh',
        bgcolor: 'background.default'
      }}
    >
      <Paper sx={{ p: 4, width: 400, display: 'flex', flexDirection: 'column', alignItems: 'center' }} elevation={3}>
        <Box
          component="img"
          src={theme.palette.mode === 'dark' ? ReversedLogo : PrimaryLogo}
          alt="GitGrader"
          sx={{ height: 48, mb: 3 }}
        />
        {meta.organizationName && (
          <Typography variant="subtitle1" color="text.secondary" gutterBottom>
            {meta.organizationName}
          </Typography>
        )}
        {error && (
          <Alert severity="error" sx={{ width: '100%', mb: 1 }}>
            {error}
          </Alert>
        )}
        <Box component="form" method="POST" onSubmit={(event) => { void handleSubmit(event); }} sx={{ width: '100%' }}>
          <TextField fullWidth margin="normal" label="Username" name="username" autoComplete="username" required />
          <TextField
            fullWidth
            margin="normal"
            label="Password"
            name="password"
            type="password"
            autoComplete="current-password"
            required
          />
          <Button type="submit" variant="contained" color="primary" fullWidth disabled={submitting} sx={{ mt: 2 }}>
            {submitting ? <CircularProgress size={24} color="inherit" /> : 'Sign in'}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
}
