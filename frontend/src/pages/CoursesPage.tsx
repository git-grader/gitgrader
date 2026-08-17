// Copyright the GitGrader contributors.
// SPDX-License-Identifier: Apache-2.0

import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useSearchParams } from 'react-router';
import { api } from '../api';
import { queryKeys } from '../api/queryKeys';
import { QueryErrorNotice } from '../components/QueryErrorNotice';
import { MutationErrorAlert, problemFieldErrors } from '../components/MutationErrorAlert';
import { useServerPagination } from '../components/useServerPagination';
import { CourseStatusChip } from '../components/CourseStatusChip';
import { fromZonedInputValue } from '../components/localDateTime';
import type { CourseDefinition } from '../api';
import { Box, TablePagination, Typography, CircularProgress, List, ListItem, ListItemText, ListItemButton, Paper, Button, Dialog, DialogTitle, DialogContent, DialogActions, TextField, FormControlLabel, Switch, FormControl, InputLabel, Select, MenuItem } from '@mui/material';
import { Link } from 'react-router';

const COURSE_STATUSES = ['DRAFT', 'ACTIVE', 'CLOSED', 'ARCHIVED'] as const;

const emptyForm = (): Partial<CourseDefinition> => ({
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

export function CoursesPage() {
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();
  const requestedStatus = searchParams.get('status');
  // An address can name a status the filter has no option for, and MUI renders a Select
  // whose value is out of range as an empty box with no way to tell what it is showing.
  const statusFilter = COURSE_STATUSES.find(candidate => candidate === requestedStatus) ?? 'ACTIVE';
  const [open, setOpen] = useState(false);
  const [form, setForm] = useState<Partial<CourseDefinition>>(emptyForm);

  const { paginationModel, setPaginationModel, params } = useServerPagination();
  const { data, isLoading, isError, refetch } = useQuery({
    queryKey: queryKeys.courses.list(statusFilter, params.page, params.size),
    queryFn: () => api.getCourses({ ...params, status: statusFilter }),
    placeholderData: (previous) => previous
  });

  function showStatus(status: string) {
    // Creating a course also switches the status filter, so it needs the same page
    // reset the filter control does. Landing on page 3 of a status that has one page
    // shows "No courses found" for a course that was just created successfully, and
    // the empty branch renders no pager to get back with.
    setPaginationModel({ ...paginationModel, page: 0 });
    const newParams = new URLSearchParams(searchParams);
    newParams.set('status', status);
    setSearchParams(newParams);
  }

  const createMutation = useMutation({
    mutationFn: (req: CourseDefinition) => api.createCourse(req),
    // The created course is in the response, so the status to switch to is the one the
    // server actually stored rather than the one the form asked for.
    onSuccess: (created) => {
      showStatus(created.status);
      void queryClient.invalidateQueries({ queryKey: queryKeys.courses.all });
      closeDialog();
    }
  });

  function closeDialog() {
    setOpen(false);
    setForm(emptyForm());
    createMutation.reset();
  }

  const handleSubmit = (e: React.SyntheticEvent) => {
    e.preventDefault();

    const toEmptyNull = (val?: string | null) => (val && val.trim() !== '') ? val : null;
    // The registration window is stated in the course's own timezone, not the reader's.
    // Converting through the browser stored a window hours away from the one typed, and
    // `new Date('').toISOString()` threw out of this handler for anything unparseable,
    // taking the filled-in form with it.
    const zone = form.timezone ?? Intl.DateTimeFormat().resolvedOptions().timeZone;

    createMutation.mutate({
      courseKey: form.courseKey ?? '',
      name: form.name ?? '',
      description: toEmptyNull(form.description),
      semester: toEmptyNull(form.semester),
      startsOn: toEmptyNull(form.startsOn),
      endsOn: toEmptyNull(form.endsOn),
      timezone: zone,
      status: form.status ?? 'DRAFT',
      registrationOpensAt: fromZonedInputValue(form.registrationOpensAt, zone),
      registrationClosesAt: fromZonedInputValue(form.registrationClosesAt, zone),
      registrationEnabled: form.registrationEnabled ?? false
    });
  };

  const fieldErrors = problemFieldErrors(createMutation.error);

  return (
    <Box sx={{ display: 'flex', flexDirection: 'column', gap: 2 }}>
      <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 1.5 }}>
        <Typography variant="h4" component="h1">Courses</Typography>
        <Box sx={{ display: 'flex', gap: 2, alignItems: 'center', flexWrap: 'wrap', flex: '1 1 auto', justifyContent: 'flex-end' }}>
          <FormControl size="small" sx={{ minWidth: { xs: '100%', sm: 200 } }}>
            <InputLabel id="course-status-filter-label">Status Filter</InputLabel>
            <Select
              labelId="course-status-filter-label"
              value={statusFilter}
              label="Status Filter"
              // A narrower filter has fewer pages, so staying on the current one would
              // ask for a page that no longer exists and show nothing.
              onChange={(e) => { showStatus(e.target.value); }}
            >
              {COURSE_STATUSES.map(status => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </Select>
          </FormControl>
          <Button variant="contained" onClick={() => setOpen(true)}>New Course</Button>
        </Box>
      </Box>

      {isLoading ? (
        <Box sx={{ display: 'flex', justifyContent: 'center', p: 4 }}>
          <CircularProgress aria-label="Loading courses" />
        </Box>
      ) : isError ? (
        <QueryErrorNotice message="The courses could not be loaded." onRetry={() => void refetch()} />
      ) : !data || data.content.length === 0 ? (
        <Paper sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary">No {statusFilter.toLowerCase()} courses found.</Typography>
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

      <Dialog open={open} onClose={() => !createMutation.isPending && closeDialog()} maxWidth="sm" fullWidth>
        <form onSubmit={handleSubmit}>
          <DialogTitle>New Course</DialogTitle>
          <DialogContent sx={{ display: 'flex', flexDirection: 'column', gap: 2, mt: 1 }}>
            <MutationErrorAlert error={createMutation.error} />
            <TextField
              label="Course Key"
              required
              fullWidth
              value={form.courseKey ?? ''}
              onChange={e => setForm({ ...form, courseKey: e.target.value })}
              error={!!fieldErrors['courseKey']}
              helperText={fieldErrors['courseKey']}
              disabled={createMutation.isPending}
            />
            <TextField
              label="Name"
              required
              fullWidth
              value={form.name ?? ''}
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
              value={form.description ?? ''}
              onChange={e => setForm({ ...form, description: e.target.value })}
              error={!!fieldErrors['description']}
              helperText={fieldErrors['description']}
              disabled={createMutation.isPending}
            />

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField
                label="Semester"
                fullWidth
                value={form.semester ?? ''}
                onChange={e => setForm({ ...form, semester: e.target.value })}
                error={!!fieldErrors['semester']}
                helperText={fieldErrors['semester']}
                disabled={createMutation.isPending}
              />
              <TextField
                label="Timezone"
                required
                fullWidth
                value={form.timezone ?? ''}
                onChange={e => setForm({ ...form, timezone: e.target.value })}
                error={!!fieldErrors['timezone']}
                helperText={fieldErrors['timezone'] ?? 'The zone the registration window below is stated in.'}
                disabled={createMutation.isPending}
              />
            </Box>

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Starts On" type="date" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.startsOn ?? ''} onChange={e => setForm({ ...form, startsOn: e.target.value })} error={!!fieldErrors['startsOn']} helperText={fieldErrors['startsOn']} disabled={createMutation.isPending} />
              <TextField label="Ends On" type="date" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.endsOn ?? ''} onChange={e => setForm({ ...form, endsOn: e.target.value })} error={!!fieldErrors['endsOn']} helperText={fieldErrors['endsOn']} disabled={createMutation.isPending} />
            </Box>

            <TextField
              select
              label="Status"
              required
              fullWidth
              value={form.status ?? ''}
              onChange={e => setForm({ ...form, status: e.target.value })}
              error={!!fieldErrors['status']}
              helperText={fieldErrors['status'] ?? 'Only an ACTIVE course accepts student registrations.'}
              disabled={createMutation.isPending}
            >
              {COURSE_STATUSES.map(status => <MenuItem key={status} value={status}>{status}</MenuItem>)}
            </TextField>

            <FormControlLabel
              control={<Switch checked={form.registrationEnabled ?? false} onChange={e => setForm({ ...form, registrationEnabled: e.target.checked })} disabled={createMutation.isPending} />}
              label="Registration Enabled"
            />

            <Box sx={{ display: 'flex', gap: 2 }}>
              <TextField label="Registration Opens At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.registrationOpensAt ?? ''} onChange={e => setForm({ ...form, registrationOpensAt: e.target.value })} error={!!fieldErrors['registrationOpensAt']} helperText={fieldErrors['registrationOpensAt']} disabled={createMutation.isPending} />
              <TextField label="Registration Closes At" type="datetime-local" fullWidth slotProps={{ inputLabel: { shrink: true } }} value={form.registrationClosesAt ?? ''} onChange={e => setForm({ ...form, registrationClosesAt: e.target.value })} error={!!fieldErrors['registrationClosesAt']} helperText={fieldErrors['registrationClosesAt']} disabled={createMutation.isPending} />
            </Box>
          </DialogContent>
          <DialogActions>
            <Button onClick={closeDialog} disabled={createMutation.isPending}>Cancel</Button>
            <Button type="submit" variant="contained" disabled={createMutation.isPending}>
              {createMutation.isPending ? 'Creating...' : 'Create'}
            </Button>
          </DialogActions>
        </form>
      </Dialog>
    </Box>
  );
}
