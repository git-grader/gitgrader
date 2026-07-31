// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useParams, Link } from 'react-router';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api';
import type { ClassDefinition } from '../api';
import { ApiProblem } from '../api/client';
import { Typography, CircularProgress, Paper, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Alert, Table, TableBody, TableCell, TableContainer, TableHead, TableRow } from '@mui/material';

export function CourseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const queryClient = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<ClassDefinition>({ classKey: '', name: '' });

  const { data: course, isLoading: courseLoading } = useQuery({
    queryKey: ['courses', id],
    queryFn: () => api.getCourse(id || '')
  });

  const { data: classes, isLoading: classesLoading } = useQuery({
    queryKey: ['courses', id, 'classes'],
    queryFn: () => api.getCourseClasses(id || '')
  });

  const createMutation = useMutation({
    mutationFn: (req: ClassDefinition) => api.createClass(id || '', req),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['courses', id, 'classes'] });
      setOpen(false);
      setForm({ classKey: '', name: '' });
    }
  });

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    createMutation.mutate(form);
  };

  if (courseLoading || classesLoading) {
    return <div style={{ display: 'flex', justifyContent: 'center', padding: '32px' }}><CircularProgress /></div>;
  }

  if (!course) {
    return <Typography color="error">Course not found</Typography>;
  }

  const err = createMutation.error as ApiProblem | null;
  const fieldErrors = err?.errors?.reduce((acc, curr) => ({ ...acc, [curr.field]: curr.message }), {} as Record<string, string>) || {};

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '24px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <div>
          <Typography variant="h4" component="h1">{course.name}</Typography>
          <Typography color="text.secondary">Key: {course.courseKey} | Status: {course.status}</Typography>
        </div>
        <Button variant="outlined" component={Link} to={`/assignments?courseId=${course.id}`}>
          View Assignments
        </Button>
      </div>

      <Paper sx={{ p: 0 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '16px' }}>
          <Typography variant="h6">Classes</Typography>
          <Button variant="contained" size="small" onClick={() => setOpen(true)}>Add Class</Button>
        </div>
        
        <TableContainer>
          <Table>
            <TableHead>
              <TableRow>
                <TableCell>Class Key</TableCell>
                <TableCell>Name</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {classes?.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={2} align="center" sx={{ py: 4, color: 'text.secondary' }}>No classes found.</TableCell>
                </TableRow>
              ) : (
                classes?.map(cls => (
                  <TableRow key={cls.id}>
                    <TableCell>{cls.classKey}</TableCell>
                    <TableCell>{cls.name}</TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </TableContainer>
      </Paper>

      <Dialog open={open} onClose={() => !createMutation.isPending && setOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit}>
          <DialogTitle>New Class</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {err && (
              <Alert severity="error">{err.detail || err.title}</Alert>
            )}
            <TextField
              label="Class Key"
              required
              fullWidth
              value={form.classKey}
              onChange={e => setForm({ ...form, classKey: e.target.value })}
              error={!!fieldErrors['classKey']}
              helperText={fieldErrors['classKey']}
              disabled={createMutation.isPending}
            />
            <TextField
              label="Name"
              required
              fullWidth
              value={form.name}
              onChange={e => setForm({ ...form, name: e.target.value })}
              error={!!fieldErrors['name']}
              helperText={fieldErrors['name']}
              disabled={createMutation.isPending}
            />
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpen(false)} disabled={createMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Adding...' : 'Add'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </div>
  );
}
