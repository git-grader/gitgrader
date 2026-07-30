// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation } from '@tanstack/react-query';
import { api } from '../api';
import type { RegistrationRequest } from '../api';
import { Box, Button, TextField, Typography, Alert, Paper, MenuItem } from '@mui/material';
import { useNavigate } from 'react-router';
import { useMeta } from '../components/MetaProvider';

export function RegistrationPage() {
  const meta = useMeta();
  const navigate = useNavigate();
  const [form, setForm] = useState<Partial<RegistrationRequest>>({});
  const [keyError, setKeyError] = useState<string | null>(null);

  const { data: avail, isLoading } = useQuery({
    queryKey: ['availability'],
    queryFn: api.getAvailability
  });

  const mutation = useMutation({
    mutationFn: api.register,
    onSuccess: (data) => {
      void navigate('/register/success', { state: { result: data } });
    }
  });

  if (isLoading) return <Box sx={{ p: 4 }}>Loading...</Box>;

  if (!avail?.open) {
    return (
      <Box sx={{ p: 4, maxWidth: 'sm', mx: 'auto' }}>
        <Alert severity="info">Registration is currently closed.</Alert>
      </Box>
    );
  }

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    setKeyError(null);
    const key = form.publicKey || '';
    if (key.includes('PRIVATE KEY') || key.includes('PuTTY-User-Key-File')) {
      setKeyError('Private key detected. Only ever paste your PUBLIC key. Never upload or share a private key.');
      setForm({ ...form, publicKey: '' });
      return;
    }
    // Client check is convenience. Server re-validates everything.
    mutation.mutate(form as RegistrationRequest);
  };

  return (
    <Box sx={{ p: 4, maxWidth: 'md', mx: 'auto' }}>
      <Paper sx={{ p: 4 }}>
        <Typography variant="h4" component="h1" gutterBottom>
          Register for {meta.name}
        </Typography>
        <form onSubmit={handleSubmit}>
          <TextField fullWidth margin="normal" label="First Name" required value={form.firstName || ''} onChange={e => setForm({ ...form, firstName: e.target.value })} />
          <TextField fullWidth margin="normal" label="Last Name" required value={form.lastName || ''} onChange={e => setForm({ ...form, lastName: e.target.value })} />
          <TextField fullWidth margin="normal" label="Student Number" required value={form.studentNumber || ''} onChange={e => setForm({ ...form, studentNumber: e.target.value })} />
          <TextField fullWidth margin="normal" label="Email" type="email" required value={form.email || ''} onChange={e => setForm({ ...form, email: e.target.value })} />
          
          <TextField select fullWidth margin="normal" label="Course" required value={form.courseKey || ''} onChange={e => setForm({ ...form, courseKey: e.target.value, classKey: '' })}>
            {avail.courses.map(c => <MenuItem key={c.courseKey} value={c.courseKey}>{c.name}</MenuItem>)}
          </TextField>
          
          <TextField select fullWidth margin="normal" label="Class" required value={form.classKey || ''} onChange={e => setForm({ ...form, classKey: e.target.value })} disabled={!form.courseKey}>
            {avail.courses.find(c => c.courseKey === form.courseKey)?.classes.map(cl => (
              <MenuItem key={cl.classKey} value={cl.classKey}>{cl.name}</MenuItem>
            )) || <MenuItem value="" disabled>Select course first</MenuItem>}
          </TextField>

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
            value={form.publicKey || ''} 
            onChange={e => setForm({ ...form, publicKey: e.target.value })}
            error={!!keyError}
            helperText={keyError || 'Starts with ssh-ed25519, ssh-rsa, etc.'}
          />
          
          <Box sx={{ mt: 2, p: 2, bgcolor: 'background.default', borderRadius: 1 }}>
            <Typography variant="body2" gutterBottom>How to generate an Ed25519 key and enable SSH commit signing:</Typography>
            <Typography variant="body2" component="pre" sx={{ fontFamily: 'monospace' }}>
              ssh-keygen -t ed25519 -C "you@example.org"{'\n'}
              git config --global gpg.format ssh{'\n'}
              git config --global user.signingkey ~/.ssh/id_ed25519.pub{'\n'}
              git config --global commit.gpgsign true
            </Typography>
          </Box>

          <Button type="submit" variant="contained" color="primary" sx={{ mt: 3 }} disabled={mutation.isPending}>
            Register
          </Button>
          {mutation.isError && (
            <Alert severity="error" sx={{ mt: 2 }}>
              Registration failed: {mutation.error.message}
            </Alert>
          )}
        </form>
      </Paper>
    </Box>
  );
}
