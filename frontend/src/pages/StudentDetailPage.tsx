// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, Chip, CircularProgress, Paper, TextField, Typography } from '@mui/material';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import type { StudentUpdate } from '../api';

export function StudentDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: queryKeys.students.detail(id), queryFn: () => api.getStudent(id), enabled: !!id });
  const student = query.data?.student;
  const sshKeys = query.data?.sshKeys ?? [];
  const [form, setForm] = useState<StudentUpdate | null>(null);
  const [keyForm, setKeyForm] = useState({ label: '', publicKey: '', reason: '' });
  const mutation = useMutation({
    mutationFn: (update: StudentUpdate) => api.updateStudent(id, update),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.students.detail(id), { student: updated, sshKeys: query.data?.sshKeys ?? [] });
      void queryClient.invalidateQueries({ queryKey: ['students', 'list'] });
      void navigate('/students');
    }
  });
  const statusMutation = useMutation({
    mutationFn: ({ status, reason }: { status: string; reason: string }) =>
      api.changeStudentStatus(id, status, reason),
    onSuccess: (updated) => {
      queryClient.setQueryData(queryKeys.students.detail(id), {
        student: { ...student, status: updated.status },
        sshKeys: query.data?.sshKeys ?? []
      });
      void queryClient.invalidateQueries({ queryKey: ['students', 'list'] });
    }
  });
  const registerKeyMutation = useMutation({
    mutationFn: () => api.registerStudentKey(id, keyForm),
    onSuccess: () => {
      setKeyForm({ label: '', publicKey: '', reason: '' });
      void queryClient.invalidateQueries({ queryKey: queryKeys.students.detail(id) });
    }
  });
  const revokeKeyMutation = useMutation({
    mutationFn: ({ keyId, reason }: { keyId: string; reason: string }) =>
      api.revokeStudentKey(id, keyId, reason),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: queryKeys.students.detail(id) });
    }
  });

  if (query.isLoading || !student && !query.isError) return <CircularProgress aria-label="Loading student" />;
  if (query.isError || !student) return <QueryErrorNotice message="The student could not be loaded." onRetry={() => void query.refetch()} />;

  const values = form ?? {
    studentUsername: student.studentUsername,
    firstName: student.firstName,
    lastName: student.lastName,
    email: student.email
  };
  const errors = problemFieldErrors(mutation.error);

  return (
    <Box sx={{ maxWidth: 720, display: 'flex', flexDirection: 'column', gap: 3 }}>
      <Typography variant="h4" component="h1" gutterBottom>Edit Student</Typography>
      <MutationErrorAlert error={statusMutation.error ?? revokeKeyMutation.error} />
      <Paper variant="outlined" sx={{ p: 2, display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
        <Typography variant="body2" color="text.secondary">Status: {student.status}</Typography>
          <Box sx={{ display: 'flex', gap: 1, flexWrap: 'wrap' }}>
            <Button size="small" variant="outlined" disabled={statusMutation.isPending} onClick={() => statusMutation.mutate({ status: 'VERIFIED_BY_INSTRUCTOR', reason: 'Verified by instructor' })}>
              Verify
            </Button>
            <Button size="small" variant="outlined" color="warning" disabled={statusMutation.isPending} onClick={() => { const reason = window.prompt('Suspension reason:', 'Suspended by instructor'); if (reason) statusMutation.mutate({ status: 'SUSPENDED', reason }); }}>
              Suspend
            </Button>
            <Button size="small" variant="outlined" color="error" disabled={statusMutation.isPending} onClick={() => { if (window.confirm('Archive this student?')) statusMutation.mutate({ status: 'ARCHIVED', reason: 'Archived by instructor' }); }}>
              Archive
            </Button>
            <Button size="small" variant="outlined" disabled={statusMutation.isPending} onClick={() => statusMutation.mutate({ status: 'RESTORE', reason: 'Restored by instructor' })}>
              Restore
            </Button>
          </Box>
      </Paper>
      <Paper component="form" sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2 }} onSubmit={(event) => {
        event.preventDefault();
        mutation.mutate(values);
      }}>
        <MutationErrorAlert error={mutation.error} />
        <TextField label="Student ID / Username" required value={values.studentUsername} onChange={(e) => setForm({ ...values, studentUsername: e.target.value })} error={!!errors.studentUsername} helperText={errors.studentUsername} disabled={mutation.isPending} />
        <TextField label="First Name" required value={values.firstName} onChange={(e) => setForm({ ...values, firstName: e.target.value })} error={!!errors.firstName} helperText={errors.firstName} disabled={mutation.isPending} />
        <TextField label="Last Name" required value={values.lastName} onChange={(e) => setForm({ ...values, lastName: e.target.value })} error={!!errors.lastName} helperText={errors.lastName} disabled={mutation.isPending} />
        <TextField label="Email" type="email" required value={values.email} onChange={(e) => setForm({ ...values, email: e.target.value })} error={!!errors.email} helperText={errors.email} disabled={mutation.isPending} />
        <Box sx={{ display: 'flex', gap: 2, justifyContent: 'flex-end' }}>
          <Button onClick={() => { void navigate('/students'); }} disabled={mutation.isPending}>Cancel</Button>
          <Button type="submit" variant="contained" disabled={mutation.isPending}>{mutation.isPending ? 'Saving...' : 'Save'}</Button>
        </Box>
      </Paper>

      <Paper variant="outlined" sx={{ p: 3, display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Typography variant="h6">SSH Keys</Typography>
        {sshKeys.length === 0 ? (
          <Typography variant="body2" color="text.secondary">No keys registered.</Typography>
        ) : (
          <Box sx={{ display: 'flex', flexDirection: 'column', gap: 1 }}>
            {sshKeys.map((key) => (
              <Paper key={key.id} variant="outlined" sx={{ p: 2, display: 'flex', flexDirection: 'column', gap: 1 }}>
                <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                  <Typography variant="subtitle2">{key.label}</Typography>
                  <Chip size="small" label={key.status} color={key.status === 'ACTIVE' ? 'success' : 'default'} />
                  <Chip size="small" variant="outlined" label={key.keyType} />
                  <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{key.fingerprint}</Typography>
                </Box>
                <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>{key.publicKey}</Typography>
                {key.status === 'ACTIVE' && (
                  <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
                    <Button
                      size="small"
                      color="warning"
                      disabled={revokeKeyMutation.isPending}
                      onClick={() => { const reason = window.prompt('Revocation reason:', 'Revoked by instructor'); if (reason) revokeKeyMutation.mutate({ keyId: key.id, reason }); }}
                    >
                      Revoke
                    </Button>
                  </Box>
                )}
              </Paper>
            ))}
          </Box>
        )}
        <MutationErrorAlert error={registerKeyMutation.error} />
        <Box component="form" sx={{ display: 'flex', flexDirection: 'column', gap: 2 }} onSubmit={(event) => { event.preventDefault(); registerKeyMutation.mutate(); }}>
          <TextField label="Label" required value={keyForm.label} onChange={(e) => setKeyForm({ ...keyForm, label: e.target.value })} disabled={registerKeyMutation.isPending} />
          <TextField label="Public Key" required multiline minRows={2} value={keyForm.publicKey} onChange={(e) => setKeyForm({ ...keyForm, publicKey: e.target.value })} disabled={registerKeyMutation.isPending} />
          <TextField label="Reason" required value={keyForm.reason} onChange={(e) => setKeyForm({ ...keyForm, reason: e.target.value })} disabled={registerKeyMutation.isPending} />
          <Box sx={{ display: 'flex', justifyContent: 'flex-end' }}>
            <Button type="submit" variant="outlined" disabled={registerKeyMutation.isPending}>
              {registerKeyMutation.isPending ? 'Registering…' : 'Register key'}
            </Button>
          </Box>
        </Box>
      </Paper>
    </Box>
  );
}
