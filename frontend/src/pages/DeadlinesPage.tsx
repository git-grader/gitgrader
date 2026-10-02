// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useEffect, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Link, useSearchParams } from 'react-router';
import { Alert, Box, Button, Chip, CircularProgress, MenuItem, Paper, Stack, TextField, Typography } from '@mui/material';
import { api, getAllPages } from '../api';
import { queryKeys } from '../api/queryKeys';
import { PageHeader } from '../components/PageHeader';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { deadlineCalendar, deadlineLabel, downloadCalendar } from '../features/calendar';

export function DeadlinesPage() {
  const [params, setParams] = useSearchParams();
  const courseId = params.get('courseId') ?? '';
  const showAll = params.get('show') === 'all';
  const [now, setNow] = useState(Date.now);
  useEffect(() => { const timer = window.setInterval(() => setNow(Date.now()), 60_000); return () => clearInterval(timer); }, []);
  const courses = useQuery({ queryKey: queryKeys.courses.choices, queryFn: () => getAllPages(api.getCourses, {}, item => item.id) });
  const assignments = useQuery({ queryKey: queryKeys.assignments.list(''), queryFn: () => getAllPages(api.getAssignments, {}, item => item.id) });
  function filter(key: string, value: string) { const next = new URLSearchParams(params); if (value) next.set(key, value); else next.delete(key); setParams(next); }
  const inScope = (assignments.data ?? []).filter(item => (!courseId || item.courseId === courseId) && item.status !== 'ARCHIVED');
  const dated = inScope.filter(item => item.dueAt && Number.isFinite(new Date(item.dueAt).getTime()));
  const visible = dated.filter(item => showAll || (item.status !== 'DRAFT' && new Date(item.dueAt ?? '').getTime() >= now))
    .sort((a, b) => new Date(a.dueAt ?? '').getTime() - new Date(b.dueAt ?? '').getTime());
  const groups = [...new Set(visible.map(item => item.courseId))];
  return <Box sx={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
    <PageHeader title="Assignment deadlines" description="Plan upcoming coursework and download deadlines to your calendar." actions={
      <Button variant="outlined" disabled={!visible.length || courses.isError || assignments.isError} onClick={() => downloadCalendar(deadlineCalendar(visible, courses.data ?? [], window.location.origin))}>Download calendar</Button>
    } />
    <Stack direction={{ xs: 'column', sm: 'row' }} spacing={2}>
      <TextField select label="Course" value={courseId} slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }} sx={{ minWidth: 200 }} onChange={event => filter('courseId', event.target.value)}>
        <MenuItem value="">All courses</MenuItem>{courses.data?.map(course => <MenuItem key={course.id} value={course.id}>{course.name}</MenuItem>)}
      </TextField>
      <TextField select label="Deadlines to show" value={showAll ? 'all' : 'upcoming'} onChange={event => filter('show', event.target.value === 'all' ? 'all' : '')}>
        <MenuItem value="upcoming">Upcoming published assignments</MenuItem><MenuItem value="all">All dated assignments, including drafts</MenuItem>
      </TextField>
    </Stack>
    <Typography variant="body2" color="text.secondary">Shows assignment deadlines in their configured timezone. Individual student extensions are not included.</Typography>
    {courses.isError && <QueryErrorNotice message="Courses could not be loaded." onRetry={() => void courses.refetch()} />}
    {assignments.isError && <QueryErrorNotice message="Deadlines could not be loaded." onRetry={() => void assignments.refetch()} />}
    {courses.isPending || assignments.isPending ? <CircularProgress aria-label="Loading deadlines" /> : !courses.isError && !assignments.isError && <>
      {inScope.length > dated.length && <Alert severity="info">{inScope.length - dated.length} assignments in this selection have no valid deadline.</Alert>}
      {!visible.length && <Typography>No deadlines match this view.</Typography>}
      {groups.map(id => <Box component="section" key={id} aria-label={`${courses.data.find(course => course.id === id)?.name ?? 'Course'} deadlines`}>
        <Typography component="h2" variant="h6" sx={{ mb: 1 }}>{courses.data.find(course => course.id === id)?.name ?? 'Unknown course'}</Typography>
        <Stack spacing={1}>{visible.filter(item => item.courseId === id).map(item => <Paper component="article" variant="outlined" key={item.id} sx={{ p: 2 }}>
          <Box sx={{ display: 'flex', alignItems: 'center', flexWrap: 'wrap', gap: 1 }}><Button component={Link} to={`/assignments/${encodeURIComponent(item.id)}`} sx={{ overflowWrap: 'anywhere', textAlign: 'left' }}>{item.title}</Button><Chip size="small" label={item.status} /></Box>
          <Typography>{deadlineLabel(item.dueAt ?? '', item.timezone || courses.data.find(course => course.id === id)?.timezone)}</Typography>
          {item.dueAt && new Date(item.dueAt).getTime() < now && <Typography variant="caption" color="text.secondary">Deadline passed</Typography>}
        </Paper>)}</Stack>
      </Box>)}
    </>}
  </Box>;
}
