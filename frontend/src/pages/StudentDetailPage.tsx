// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useNavigate, useParams } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Box, Button, CircularProgress, Paper, TextField, Typography } from '@mui/material';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import type { StudentUpdate } from '../api';

export function StudentDetailPage() {
  const { id = '' } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const query = useQuery({ queryKey: ['students', 'detail', id], queryFn: () => api.getStudent(id), enabled: !!id });
  const student = query.data?.student;
  const [form, setForm] = useState<StudentUpdate | null>(null);
  const mutation = useMutation({
    mutationFn: (update: StudentUpdate) => api.updateStudent(id, update),
    onSuccess: (updated) => {
      queryClient.setQueryData(['students', 'detail', id], { student: updated, sshKeys: query.data?.sshKeys ?? [] });
      void queryClient.invalidateQueries({ queryKey: queryKeys.students.list('', '') });
      void navigate('/students');
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
    <Box sx={{ maxWidth: 720 }}>
      <Typography variant="h4" component="h1" gutterBottom>Edit Student</Typography>
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
    </Box>
  );
}
