// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { api, RegistrationRequestSchema } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import type { RegistrationRequest } from '../api';
import { Box, Button, TextField, Typography, Alert, Paper, MenuItem, CircularProgress } from '@mui/material';
import { useNavigate } from 'react-router';
import { useMeta } from '../components/MetaProvider';
import { BrandMark } from '../components/BrandMark';

export function RegistrationPage() {
  const meta = useMeta();
  const navigate = useNavigate();
  const [form, setForm] = useState<Partial<RegistrationRequest>>({});
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const { data: avail, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.availability,
    queryFn: api.getAvailability
  });

  const mutation = useMutation({
    mutationFn: api.register,
    onSuccess: (data) => {
      void navigate('/register/success', { state: { result: data, courseKey: form.courseKey } });
    }
  });

  if (isLoading) {
    return (
      <Box role="status" sx={{ display: 'flex', justifyContent: 'center', p: 8 }}>
        <CircularProgress aria-label="Checking whether registration is open" />
      </Box>
    );
  }

  // Whether registration is open is a decision only the server can make. Reading a
  // failed call as a closed one turned any hiccup into a refusal, and told a student
  // within the window that they had missed it.
  if (isError) {
    return (
      <Box sx={{ p: 4, maxWidth: 'sm', mx: 'auto' }}>
        <QueryErrorNotice
          message="Whether registration is open could not be checked. Try again in a moment."
          onRetry={() => void refetch()}
        />
      </Box>
    );
  }

  if (!avail?.open) {
    return (
      <Box sx={{ p: 4, maxWidth: 'sm', mx: 'auto' }}>
        <Alert severity="info">Registration is currently closed.</Alert>
      </Box>
    );
  }

  const selectedCourse = avail.courses.find(c => c.courseKey === form.courseKey);
  // A course need not be divided into classes, and the server accepts a registration
  // without one. Requiring it against an empty dropdown made such a course impossible to
  // register for, with nothing on screen explaining why the form would not submit.
  const classes = selectedCourse?.classes ?? [];
  const serverFieldErrors = problemFieldErrors(mutation.error);
  const errorFor = (field: string) => fieldErrors[field] ?? serverFieldErrors[field];

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    setFieldErrors({});
    const key = form.publicKey ?? '';
    if (key.includes('PRIVATE KEY') || key.includes('PuTTY-User-Key-File')) {
      setFieldErrors({
        publicKey: 'Private key detected. Only ever paste your PUBLIC key. Never upload or share a private key.'
      });
      setForm({ ...form, publicKey: '' });
      return;
    }
    // Client check is convenience. Server re-validates everything.
    const parsed = RegistrationRequestSchema.safeParse(form);
    if (!parsed.success) {
      const errors: Record<string, string> = {};
      for (const issue of parsed.error.issues) {
        const field = issue.path[0];
        if (typeof field === 'string' && !(field in errors)) {
          errors[field] = issue.message;
        }
      }
      setFieldErrors(errors);
      return;
    }
    mutation.mutate(parsed.data);
  };

  return (
    <Box sx={{ p: 4, maxWidth: 'md', mx: 'auto' }}>
      <BrandMark />
      <Paper sx={{ p: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Register for {meta.name}
        </Typography>
        <form onSubmit={handleSubmit}>
          <TextField fullWidth margin="normal" label="First Name" required value={form.firstName ?? ''} onChange={e => setForm({ ...form, firstName: e.target.value })} error={!!errorFor('firstName')} helperText={errorFor('firstName')} />
          <TextField fullWidth margin="normal" label="Last Name" required value={form.lastName ?? ''} onChange={e => setForm({ ...form, lastName: e.target.value })} error={!!errorFor('lastName')} helperText={errorFor('lastName')} />
          <TextField fullWidth margin="normal" label="Student Number" required value={form.studentNumber ?? ''} onChange={e => setForm({ ...form, studentNumber: e.target.value })} error={!!errorFor('studentNumber')} helperText={errorFor('studentNumber')} />
          <TextField fullWidth margin="normal" label="Email" type="email" required value={form.email ?? ''} onChange={e => setForm({ ...form, email: e.target.value })} error={!!errorFor('email')} helperText={errorFor('email')} />

          <TextField select fullWidth margin="normal" label="Course" required value={form.courseKey ?? ''} onChange={e => setForm({ ...form, courseKey: e.target.value, classKey: '' })} error={!!errorFor('courseKey')} helperText={errorFor('courseKey')}>
            {avail.courses.map(c => <MenuItem key={c.courseKey} value={c.courseKey}>{c.name}</MenuItem>)}
          </TextField>

          {classes.length > 0 && (
            <TextField select fullWidth margin="normal" label="Class" required value={form.classKey ?? ''} onChange={e => setForm({ ...form, classKey: e.target.value })} error={!!errorFor('classKey')} helperText={errorFor('classKey')}>
              {classes.map(cl => (
                <MenuItem key={cl.classKey} value={cl.classKey}>{cl.name}</MenuItem>
              ))}
            </TextField>
          )}
          {form.courseKey && classes.length === 0 && (
            <Alert severity="info" sx={{ mt: 2 }}>
              This course is not divided into classes, so there is nothing to choose here.
            </Alert>
          )}

          <Alert severity="warning" sx={{ mt: 2, mb: 2 }}>
            Only ever paste your PUBLIC key. Never upload or share a private key.
          </Alert>
          <TextField
            fullWidth
            margin="normal"
            label="SSH Public Key"
            multiline
            rows={4}
            required
            value={form.publicKey ?? ''}
            onChange={e => setForm({ ...form, publicKey: e.target.value })}
            error={!!errorFor('publicKey')}
            helperText={errorFor('publicKey') ?? 'Starts with ssh-ed25519, ssh-rsa, etc.'}
          />

          <Box sx={{ mt: 2, p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
            <Typography variant="body2" gutterBottom>How to generate an Ed25519 key and enable SSH commit signing:</Typography>
            <Typography
              variant="body2"
              component="pre"
              // A pre keeps its longest line intact, and the git config commands here are
              // longer than a phone is wide, which pushed the whole registration page
              // sideways. Students register on phones, so it wraps instead.
              sx={{ fontFamily: 'monospace', whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', m: 0 }}
            >
              ssh-keygen -t ed25519 -C "you@example.org"{'\n'}
              git config --global gpg.format ssh{'\n'}
              git config --global user.signingkey ~/.ssh/id_ed25519.pub{'\n'}
              git config --global commit.gpgsign true
            </Typography>
          </Box>

          <Button type="submit" variant="contained" color="primary" sx={{ mt: 3 }} disabled={mutation.isPending}>
            {mutation.isPending ? 'Registering...' : 'Register'}
          </Button>
          {/* The server explains a refusal in `detail` and names the offending fields in
              `errors`. Showing only `message` reduced "that student number is already
              registered" to "Registration failed: Bad Request". */}
          <MutationErrorAlert error={mutation.error} sx={{ mt: 2 }} />
        </form>
      </Paper>
    </Box>
  );
}
