// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import { api } from '../api';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { useServerPagination } from '../components/useServerPagination';
import { CourseStatusChip } from '../components/CourseStatusChip';
import type { CourseDefinition } from '../api';
import { ApiProblem } from '../api/client';
import { Box, TablePagination, Typography, CircularProgress, List, ListItem, ListItemText, ListItemButton, Paper, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField, Alert, FormControlLabel, Switch, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import { Link } from 'react-router';

export function CoursesPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const statusFilter = searchParams.get('status') || 'ACTIVE';
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Partial<CourseDefinition>>({ 
    courseKey: '', 
    name: '', 
    description: '', 
    semester: '',
    startsOn: '',
    endsOn: '',
    timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
    status: 'DRAFT', 
    registrationOpensAt: '',
    registrationClosesAt: '',
    registrationEnabled: false 
  });

  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: ['courses', statusFilter, params.page, params.size],
    queryFn: () => api.getCourses({ ...params, status: statusFilter }),
    placeholderData: (previous) => previous
  });

  const createMutation = useMutation({
    mutationFn: (req: CourseDefinition) => api.createCourse(req),
    onSuccess: (_data, variables) => {
      const newStatus = variables.status || 'DRAFT';
      // Creating a course also switches the status filter, so it needs the same page
      // reset the filter control does. Landing on page 3 of a status that has one page
      // shows "No courses found" for a course that was just created successfully, and
      // the empty branch renders no pager to get back with.
      setPaginationModel({ ...paginationModel, page: 0 });
      const newParams = new URLSearchParams(searchParams);
      newParams.set('status', newStatus);
      setSearchParams(newParams);
      void queryClient.invalidateQueries({ queryKey: ['courses', newStatus] });
      setOpen(false);
      setForm({ 
        courseKey: '', name: '', description: '', semester: '',
        startsOn: '', endsOn: '', timezone: Intl.DateTimeFormat().resolvedOptions().timeZone,
        status: 'DRAFT', registrationOpensAt: '', registrationClosesAt: '', registrationEnabled: false 
      });
    }
  });

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();
    
    const toIso = (val?: string | null) => val ? new Date(val).toISOString() : null;
    const toEmptyNull = (val?: string | null) => (val && val.trim() !== '') ? val : null;

    const payload: CourseDefinition = {
      courseKey: form.courseKey || '',
      name: form.name || '',
      description: toEmptyNull(form.description),
      semester: toEmptyNull(form.semester),
      startsOn: toEmptyNull(form.startsOn),
      endsOn: toEmptyNull(form.endsOn),
      timezone: form.timezone || Intl.DateTimeFormat().resolvedOptions().timeZone,
      status: form.status || 'DRAFT',
      registrationOpensAt: toIso(form.registrationOpensAt),
      registrationClosesAt: toIso(form.registrationClosesAt),
      registrationEnabled: form.registrationEnabled ?? false
    };

    createMutation.mutate(payload);
  };

  const err = createMutation.error as ApiProblem | null;
  const fieldErrors = err?.errors?.reduce((acc, curr) => ({ ...acc, [curr.field]: curr.message }), {} as Record<string, string>) || {};

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: '12px' }}>
        <Typography variant="h4" component="h1">Courses</Typography>
                <div style={{ display: 'flex', gap: '16px', alignItems: 'center', flexWrap: 'wrap', flex: '1 1 auto', justifyContent: 'flex-end' }}>
          <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 200 } }}>
            <InputLabel>Status Filter</InputLabel>
            <Select
              value={statusFilter}
              label="Status Filter"
              onChange={(e) => {
                // A narrower filter has fewer pages, so staying on the current one would
                // ask for a page that no longer exists and show nothing.
                setPaginationModel({ ...paginationModel, page: 0 });
                const newParams = new URLSearchParams(searchParams);
                newParams.set('status', e.target.value);
                setSearchParams(newParams);
              }}
            >
              <MenuItem value="DRAFT">DRAFT</MenuItem>
              <MenuItem value="ACTIVE">ACTIVE</MenuItem>
              <MenuItem value="CLOSED">CLOSED</MenuItem>
              <MenuItem value="ARCHIVED">ARCHIVED</MenuItem>
            </Select>
          </FormControl>
          <Button variant="contained" onClick={() => setOpen(true)}>New Course</Button>
        </div>
      </div>

      {isLoading ? (
        <div style={{ display: 'flex', justifyContent: 'center', padding: '32px' }}><CircularProgress /></div>
      ) : isError ? (
        <QueryErrorNotice message="The courses could not be loaded." onRetry={() => void refetch()} />
      ) : !data?.content || data.content.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary">No courses found.</Typography>
        </Paper>
      ) : (
        <Paper sx={{ p: 2 }}>
          <List>
            {data.content.map(c => (
              <ListItem key={c.id} disablePadding>
                <ListItemButton component={Link} to={`/courses/${c.id}`}>
                  <ListItemText 
                    primary={c.name} 
                    slotProps={{ secondary: { component: 'div' } }}
                    secondary={
                      <Box sx={{ display: 'flex', gap: 1, alignItems: 'center', flexWrap: 'wrap' }}>
                        <span>Key: {c.courseKey}</span>
                        <CourseStatusChip status={c.status} />
                      </Box>
                    }
                  />
                </ListItemButton>
              </ListItem>
            ))}
          </List>
          {/* A List has no pager of its own, and the endpoint returns one page, so
              without this every course past the first page was unreachable. */}
          <TablePagination
            component="div"
            count={data.totalElements}
            page={paginationModel.page}
            onPageChange={(_e, page) => { setPaginationModel({ ...paginationModel, page }); }}
            rowsPerPage={paginationModel.pageSize}
            onRowsPerPageChange={(e) => { setPaginationModel({ page: 0, pageSize: Number(e.target.value) }); }}
            rowsPerPageOptions={[20, 50, 100]}
          />
        </Paper>
      )}

      <Dialog open={open} onClose={() => !createMutation.isPending && setOpen(false)} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit}>
          <DialogTitle>New Course</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            {err && (
              <Alert severity="error">{err.detail || err.title}</Alert>
            )}
            <TextField
              label="Course Key"
              required
              fullWidth
              value={form.courseKey}
              onChange={e => setForm({ ...form, courseKey: e.target.value })}
              error={!!fieldErrors['courseKey']}
              helperText={fieldErrors['courseKey']}
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
            <TextField
              label="Description"
              fullWidth
              multiline
              rows={2}
              value={form.description || ''}
              onChange={e => setForm({ ...form, description: e.target.value })}
              error={!!fieldErrors['description']}
              helperText={fieldErrors['description']}
              disabled={createMutation.isPending}
            />
            
            <div style={{ display: 'flex', gap: '16px' }}>
              <TextField
                label="Semester"
                fullWidth
                value={form.semester || ''}
                onChange={e => setForm({ ...form, semester: e.target.value })}
                error={!!fieldErrors['semester']}
                helperText={fieldErrors['semester']}
                disabled={createMutation.isPending}
              />
              <TextField
                label="Timezone"
                required
                fullWidth
                value={form.timezone || ''}
                onChange={e => setForm({ ...form, timezone: e.target.value })}
                error={!!fieldErrors['timezone']}
                helperText={fieldErrors['timezone']}
                disabled={createMutation.isPending}
              />
            </div>
            
            <div style={{ display: 'flex', gap: '16px' }}>
              <TextField label="Starts On" type="date" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.startsOn || ''} onChange={e => setForm({ ...form, startsOn: e.target.value })} error={!!fieldErrors['startsOn']} helperText={fieldErrors['startsOn']} disabled={createMutation.isPending} />
              <TextField label="Ends On" type="date" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.endsOn || ''} onChange={e => setForm({ ...form, endsOn: e.target.value })} error={!!fieldErrors['endsOn']} helperText={fieldErrors['endsOn']} disabled={createMutation.isPending} />
            </div>

            <TextField
              select
              label="Status"
              required
              fullWidth
              value={form.status || ''}
              onChange={e => setForm({ ...form, status: e.target.value })}
              error={!!fieldErrors['status']}
              helperText={fieldErrors['status'] || 'Only an ACTIVE course accepts student registrations.'}
              disabled={createMutation.isPending}
            >
                <MenuItem value="DRAFT">DRAFT</MenuItem>
                <MenuItem value="ACTIVE">ACTIVE</MenuItem>
                <MenuItem value="CLOSED">CLOSED</MenuItem>
                <MenuItem value="ARCHIVED">ARCHIVED</MenuItem>
              </TextField>
            
            <FormControlLabel 
              control={<Switch checked={form.registrationEnabled} onChange={e => setForm({ ...form, registrationEnabled: e.target.checked })} disabled={createMutation.isPending} />} 
              label="Registration Enabled" 
            />
            
            <div style={{ display: 'flex', gap: '16px' }}>
              <TextField label="Registration Opens At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.registrationOpensAt || ''} onChange={e => setForm({ ...form, registrationOpensAt: e.target.value })} error={!!fieldErrors['registrationOpensAt']} helperText={fieldErrors['registrationOpensAt']} disabled={createMutation.isPending} />
              <TextField label="Registration Closes At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.registrationClosesAt || ''} onChange={e => setForm({ ...form, registrationClosesAt: e.target.value })} error={!!fieldErrors['registrationClosesAt']} helperText={fieldErrors['registrationClosesAt']} disabled={createMutation.isPending} />
            </div>
          </DialogContent>
          <DialogActions>
            <Button onClick={() => setOpen(false)} disabled={createMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating...' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </div>
  );
}
