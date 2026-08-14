// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import type { RuntimeDefinition } from '../api';
import { ApiProblem } from '../api/client';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { Box, Typography, CircularProgress, List, ListItem, ListItemText, Button, Dialog, DialogTitle, DialogContent, TextField, FormControlLabel, Checkbox, Alert } from '@mui/material';

export function AdminRuntimesPage() {
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Partial<RuntimeDefinition>>({
    enabled: true,
    reportFormat: 'JUNIT'
  });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const { data: me, isLoading: meLoading, isError: meFailed, refetch: refetchMe } = useQuery({
    queryKey: ['me'],
    queryFn: () => api.getMe()
  });

  const { data: runtimes, isLoading: runtimesLoading, isError: runtimesFailed, refetch: refetchRuntimes } = useQuery({
    queryKey: ['runtimes'],
    queryFn: () => api.getRuntimes()
  });

  const createMutation = useMutation({
    mutationFn: (req: RuntimeDefinition) => api.createRuntime(req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['runtimes'] });
      setOpen(false);
      setForm({ enabled: true, reportFormat: 'JUNIT' });
      setFieldErrors({});
    }
  });

  if (runtimesLoading || meLoading) return <Box sx={{ p: 4 }}><CircularProgress /></Box>;
  // Who is signed in decides whether the create control appears at all, so a failed
  // `me` call would quietly present a read-only page to an administrator.
  if (runtimesFailed || meFailed) {
    return (
      <QueryErrorNotice
        message="The runtimes could not be loaded."
        onRetry={() => {
          void refetchRuntimes();
          void refetchMe();
        }}
      />
    );
  }
  if (!runtimes) return null;

  const isAdmin = me?.roles.includes('ROLE_ADMIN');
  const err = createMutation.error as ApiProblem | null;

  const validate = (): boolean => {
    const errors: Record<string, string> = {};
    if (!form.runtimeKey) errors['runtimeKey'] = 'Required';
    if (!form.displayName) errors['displayName'] = 'Required';
    if (!form.image) errors['image'] = 'Required';
    if (!form.tag) errors['tag'] = 'Required';
    else if (form.tag === 'latest') errors['tag'] = "tag 'latest' is not reproducible";
    if (!form.imageDigest) errors['imageDigest'] = 'Required';
    else if (!/^sha256:[a-f0-9]{64}$/.test(form.imageDigest)) errors['imageDigest'] = 'Must be a valid sha256 digest';
    if (!form.testCommand) errors['testCommand'] = 'Required';
    if (!form.reportFormat) errors['reportFormat'] = 'Required';
    
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    if (!validate()) return;
    
    createMutation.mutate(form as RuntimeDefinition);
  };

  return (
    <Box>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
        <Typography variant="h4" component="h1">Runtimes</Typography>
        {isAdmin ? (
          <Button variant="contained" onClick={() => setOpen(true)}>New Runtime</Button>
        ) : (
          <Typography color="text.secondary">An administrator must add runtimes.</Typography>
        )}
      </Box>

      {runtimes.length === 0 ? (
        <Alert severity="info">No runtimes configured. At least one runtime is required to publish assignments.</Alert>
      ) : (
        <List>
          {runtimes.map(rt => (
            <ListItem key={rt.id}>
              <ListItemText primary={rt.displayName} secondary={
                <span style={{ display: 'flex', flexDirection: 'column' }}>
                  <span>{rt.image}:{rt.tag}</span>
                  <span style={{ fontFamily: 'monospace', fontSize: '0.875rem' }}>{rt.imageDigest.substring(0, 7)}...{rt.imageDigest.substring(rt.imageDigest.length - 7)}</span>
                </span>
              } />
            </ListItem>
          ))}
        </List>
      )}

      <Dialog open={open} onClose={() => !createMutation.isPending && setOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit}>
          <DialogTitle>New Runtime</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {err && <Alert severity="error">{err.detail || err.title}</Alert>}
            
            <TextField label="Key" required fullWidth value={form.runtimeKey || ''} onChange={e => setForm({ ...form, runtimeKey: e.target.value })} error={!!fieldErrors['runtimeKey']} helperText={fieldErrors['runtimeKey']} disabled={createMutation.isPending} />
            <TextField label="Display Name" required fullWidth value={form.displayName || ''} onChange={e => setForm({ ...form, displayName: e.target.value })} error={!!fieldErrors['displayName']} helperText={fieldErrors['displayName']} disabled={createMutation.isPending} />
            <TextField label="Image" required fullWidth value={form.image || ''} onChange={e => setForm({ ...form, image: e.target.value })} error={!!fieldErrors['image']} helperText={fieldErrors['image']} disabled={createMutation.isPending} />
            <TextField label="Tag" required fullWidth value={form.tag || ''} onChange={e => setForm({ ...form, tag: e.target.value })} error={!!fieldErrors['tag']} helperText={fieldErrors['tag']} disabled={createMutation.isPending} />
            <TextField label="Image Digest" required fullWidth value={form.imageDigest || ''} onChange={e => setForm({ ...form, imageDigest: e.target.value })} error={!!fieldErrors['imageDigest']} helperText={fieldErrors['imageDigest']} disabled={createMutation.isPending} />
            <TextField label="Install Command (Optional)" fullWidth value={form.installCommand || ''} onChange={e => setForm({ ...form, installCommand: e.target.value || null })} disabled={createMutation.isPending} />
            <TextField label="Test Command" required fullWidth value={form.testCommand || ''} onChange={e => setForm({ ...form, testCommand: e.target.value })} error={!!fieldErrors['testCommand']} helperText={fieldErrors['testCommand']} disabled={createMutation.isPending} />
            <TextField label="Report Format" required fullWidth value={form.reportFormat || ''} onChange={e => setForm({ ...form, reportFormat: e.target.value })} error={!!fieldErrors['reportFormat']} helperText={fieldErrors['reportFormat']} disabled={createMutation.isPending} />
            <FormControlLabel control={<Checkbox checked={form.enabled || false} onChange={e => setForm({ ...form, enabled: e.target.checked })} disabled={createMutation.isPending} />} label="Enabled" />
          </DialogContent>
          <Box sx={{ p: 2, display: 'flex', justifyContent: 'flex-end', gap: 1 }}>
            <Button onClick={() => setOpen(false)} disabled={createMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createMutation.isPending}>Create Runtime</Button>
          </Box>
        </form>
      </Dialog>
    </Box>
  );
}
