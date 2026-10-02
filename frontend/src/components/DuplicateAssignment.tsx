// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useNavigate } from 'react-router';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Alert, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, TextField } from '@mui/material';
import { api, AssignmentDefinitionSchema, getAllPages } from '../api';
import type { AssignmentDetail } from '../api';
import { queryKeys } from '../api/queryKeys';
import { MutationErrorAlert } from './MutationErrorAlert';
import { fromZonedInputValue, toZonedInputValue } from './localDateTime';

export function DuplicateAssignment({ assignment }: { assignment: AssignmentDetail }) {
  const navigate = useNavigate();
  const client = useQueryClient();
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState({ courseId: assignment.courseId, key: `${assignment.assignmentKey}-copy`, title: `${assignment.title} (copy)`, opensAt: '', dueAt: '' });
  const [error, setError] = useState('');
  const courses = useQuery({ queryKey: queryKeys.courses.choices, queryFn: () => getAllPages(api.getCourses, {}, course => course.id), enabled: open });
  const timezone = assignment.timezone || courses.data?.find(course => course.id === form.courseId)?.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone;
  const mutation = useMutation({ mutationFn: api.createAssignment, onSuccess: created => {
    void client.invalidateQueries({ queryKey: queryKeys.assignments.all });
    void client.invalidateQueries({ queryKey: queryKeys.dashboard });
    setOpen(false); void navigate(`/assignments/${encodeURIComponent(created.id)}`);
  } });
  function submit(event: React.SyntheticEvent) {
    event.preventDefault();
    const opensAt = fromZonedInputValue(form.opensAt, timezone);
    const dueAt = fromZonedInputValue(form.dueAt, timezone);
    if (!form.key.trim() || !form.title.trim() || !form.courseId) { setError('Choose a course, key and title.'); return; }
    if (form.courseId === assignment.courseId && form.key.trim() === assignment.assignmentKey) { setError('Use a new assignment key.'); return; }
    if ((form.opensAt && !opensAt) || (form.dueAt && !dueAt) || (opensAt && dueAt && opensAt >= dueAt)) { setError('Use valid dates with the deadline after the opening date.'); return; }
    // Parsing strips response-only fields such as id. Duplication never publishes.
    mutation.mutate(AssignmentDefinitionSchema.parse({ ...assignment, courseId: form.courseId, assignmentKey: form.key.trim(), title: form.title.trim(), status: 'DRAFT', timezone, opensAt, dueAt }));
  }
  return <>
    <Button variant="outlined" onClick={() => {
      setForm({ courseId: assignment.courseId, key: `${assignment.assignmentKey}-copy`, title: `${assignment.title} (copy)`, opensAt: '', dueAt: '' });
      setError(''); mutation.reset(); setOpen(true);
    }}>Duplicate as draft</Button>
    <Dialog open={open} onClose={() => { if (!mutation.isPending) setOpen(false); }} fullWidth maxWidth="sm">
      <form onSubmit={submit}><DialogTitle>Duplicate assignment as draft</DialogTitle><DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
        <Alert severity="info">Copies materials, grading settings and sandbox limits. Choose a new key and review the dates. The copy remains a draft.</Alert>
        <MutationErrorAlert error={mutation.error} />
        {error && <Alert severity="error">{error}</Alert>}
        {courses.isError && <Alert severity="error">Courses could not be loaded. <Button onClick={() => void courses.refetch()}>Retry</Button></Alert>}
        <TextField select label="Course" required value={form.courseId} disabled={mutation.isPending || courses.isPending} onChange={event => setForm({ ...form, courseId: event.target.value })}>
          {!courses.data?.some(course => course.id === form.courseId) && <MenuItem value={form.courseId}>Original course</MenuItem>}
          {courses.data?.map(course => <MenuItem key={course.id} value={course.id}>{course.name}</MenuItem>)}
        </TextField>
        <TextField label="New assignment key" required value={form.key} disabled={mutation.isPending} onChange={event => setForm({ ...form, key: event.target.value })} />
        <TextField label="New title" required value={form.title} disabled={mutation.isPending} onChange={event => setForm({ ...form, title: event.target.value })} />
        <Alert severity="info">Dates use {timezone}. Dates start empty to avoid reusing an old deadline.</Alert>
        <TextField label="Opening date" type="datetime-local" slotProps={{ inputLabel: { shrink: true } }} value={form.opensAt} disabled={mutation.isPending} onChange={event => setForm({ ...form, opensAt: event.target.value })} />
        <TextField label="Deadline" type="datetime-local" slotProps={{ inputLabel: { shrink: true } }} value={form.dueAt} disabled={mutation.isPending} onChange={event => setForm({ ...form, dueAt: event.target.value })} />
        <Button disabled={mutation.isPending} onClick={() => setForm({ ...form, opensAt: toZonedInputValue(assignment.opensAt, timezone), dueAt: toZonedInputValue(assignment.dueAt, timezone) })}>Use original dates</Button>
      </DialogContent><DialogActions><Button disabled={mutation.isPending} onClick={() => setOpen(false)}>Cancel</Button><Button type="submit" variant="contained" disabled={mutation.isPending || courses.isPending || courses.isError}>{mutation.isPending ? 'Creating draft…' : 'Create draft'}</Button></DialogActions></form>
    </Dialog>
  </>;
}
