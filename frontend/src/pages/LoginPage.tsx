// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useRef, useState } from 'react';
import { useLocation } from 'react-router';
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

/**
 * Where to go once signed in, taking only a path this application serves.
 *
 * The layout records where an expired session interrupted, and sending everyone to the
 * dashboard instead meant a deep link never survived signing back in. Anything that is
 * not a single-slash absolute path is discarded: `//elsewhere.example` is a same-origin
 * looking value that navigates off the site.
 */
function intendedPath(state: unknown): string {
  if (state && typeof state === 'object' && 'from' in state) {
    const from = state.from;
    if (from && typeof from === 'object' && 'pathname' in from) {
      const pathname = from.pathname;
      if (typeof pathname === 'string' && pathname.startsWith('/') && !pathname.startsWith('//')) {
        return pathname;
      }
    }
  }
  return '/';
}

export function LoginPage() {
  const theme = useTheme();
  const meta = useMeta();
  const location = useLocation();
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const errorRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (error) {
      errorRef.current?.focus();
    }
  }, [error]);

  async function handleSubmit(event: React.SyntheticEvent<HTMLFormElement>) {
    event.preventDefault();
    const form = new FormData(event.currentTarget);
    setSubmitting(true);
    setError(null);
    try {
      const response = await postForm('/login', {
        username: text(form, 'username'),
        password: text(form, 'password')
      });
      // A refused sign-in answers with a redirect, so a 403 is not a wrong password: it
      // is the request being rejected before the password was read, which in practice
      // means the cross-site request token was missing or stale. Reporting that as bad
      // credentials sent people to reset a password that was never the problem.
      if (response.status === 403) {
        setError('The sign-in request was rejected for security reasons. Reload the page and try again.');
        return;
      }
      if (!response.ok) {
        setError('The service could not be reached. Try again.');
        return;
      }
      // The redirect means the response alone does not say whether it worked. Asking who
      // we are now does.
      const me = await fetch('/api/v1/me', { headers: { Accept: 'application/json' } });
      if (!me.ok) {
        setError('Those credentials were not accepted.');
        return;
      }
      window.location.assign(intendedPath(location.state));
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
          <Alert ref={errorRef} severity="error" tabIndex={-1} sx={{ width: '100%', mb: 1 }}>
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
            {submitting ? <CircularProgress size={24} color="inherit" aria-label="Signing in" /> : 'Sign in'}
          </Button>
        </Box>
      </Paper>
    </Box>
  );
}
